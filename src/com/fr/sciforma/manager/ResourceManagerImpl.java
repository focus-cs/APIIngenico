/*
 * © 2008 Sciforma. Tous droits réservés. 
 */
package com.fr.sciforma.manager;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;


import com.sciforma.psnext.api.DataFormatException;
import com.sciforma.psnext.api.PSException;
import com.sciforma.psnext.api.Resource;
import com.sciforma.psnext.api.Session;

import fr.sciforma.psconnect.exception.BusinessException;
import fr.sciforma.psconnect.exception.TechnicalException;
import org.pmw.tinylog.Logger;

/**
 * implementation du ResourceManager
 */
public class ResourceManagerImpl implements ResourceManager {


	private Session session;

	private boolean hasResourceCache = true;

	private boolean usePublishedResources = false;

	private boolean addInactiveResources = false;

	private String codeField = RESOURCE_CODE;

	private Map<Double, Resource> resourcesByInternalId = new HashMap<Double, Resource>();

	private Map<String, Resource> resourcesById = new HashMap<String, Resource>();

	private Map<String, Set<Resource>> resourcesByCode = new HashMap<String, Set<Resource>>();

	public ResourceManagerImpl(Session session) {
		this.session = session;
	}

	/**
	 * @param usePublishedResources
	 *            the usePublishedResources to set
	 */
	public void setUsePublishedResources(boolean usePublishedResources) {
		this.usePublishedResources = usePublishedResources;
	}

	/**
	 * fluent setter
	 * 
	 * @param usePublishedResources
	 *            the usePublishedResources to set
	 */
	public ResourceManagerImpl withUsePublishedResources(
			boolean usePublishedResources) {
		this.usePublishedResources = usePublishedResources;
		return this;
	}

	/**
	 * @param hasResourceCache
	 *            the hasResourceCache to set
	 */
	public void setHasResourceCache(boolean hasResourceCache) {
		this.hasResourceCache = hasResourceCache;
	}

	/**
	 * fluent setter
	 * 
	 * @param hasResourceCache
	 *            the hasResourceCache to set
	 */
	public ResourceManagerImpl withHasResourceCache(boolean hasResourceCache) {
		this.hasResourceCache = hasResourceCache;
		return this;
	}

	/**
	 * @param addInactiveResources
	 *            the addInactiveResources to set
	 */
	public void setAddInactiveResources(boolean addInactiveResources) {
		this.addInactiveResources = addInactiveResources;
	}

	/**
	 * fluent setter
	 * 
	 * @param addInactiveResources
	 *            the addInactiveResources to set
	 */
	public ResourceManagerImpl withAddInactiveResources(
			boolean addInactiveResources) {
		this.addInactiveResources = addInactiveResources;
		return this;
	}

	public Resource findResourceByInternalId(final Long internalId)
			throws BusinessException, TechnicalException {
		if (hasResourceCache && resourcesByInternalId.get(internalId) != null) {
			return resourcesByInternalId.get(internalId);
		}

		List<Resource> findAndCacheByFilter = findAndCacheByFilter(
				new ResourceFilter() {
					public boolean filter(Resource resource) throws PSException {
						return internalId.doubleValue() == resource
								.getDoubleField("Internal Id");
					}
				}, true);

		return findAndCacheByFilter.isEmpty() ? null : findAndCacheByFilter
				.iterator().next();
	}

	public Resource findResourceById(final String id) throws BusinessException,
			TechnicalException {
		if (hasResourceCache && resourcesById.get(id) != null) {
			return resourcesById.get(id);
		}

		List<Resource> findAndCacheByFilter = findAndCacheByFilter(
				new ResourceFilter() {
					public boolean filter(Resource resource) throws PSException {
						return id != null
								&& id.equals(resource.getStringField("ID"));
					}
				}, true);

		return findAndCacheByFilter.isEmpty() ? null : findAndCacheByFilter
				.iterator().next();
	}

	public Resource findResourceByCode(final String code)
			throws BusinessException, TechnicalException {
		Collection<Resource> find = null;

		if (hasResourceCache) {
			find = resourcesByCode.get(code);
		}

		if (!hasResourceCache || find == null || find.isEmpty()) {
			find = findAndCacheByFilter(new ResourceFilter() {
				public boolean filter(Resource resource) throws PSException {
					return code.equals(resource.getStringField(codeField));
				}
			}, true);
		}

		if (find.size() == 1) {
			// cas normal
			return find.iterator().next();
		}

		if (find.size() == 0) {
			// cas pas de ressource trouvée
			return null;
		} else {
			// cas plus d'une ressource trouvée
			Logger.warn("attention : il y a plusieurs ressources <" + find.size()
					+ "> avec le même <" + codeField + "> - <" + code + ">");

				Logger.debug("Ressources avec le même <" + codeField + "> - <"
						+ code + "> : ");
				for (Resource resource : find) {
					Logger.debug("* <" + resource + ">");
				}

			return find.iterator().next();
		}
	}

	public List<Resource> findResourceByCriteria(ResourceFilter resourceFilter)
			throws BusinessException, TechnicalException {
		return findAndCacheByFilter(resourceFilter, false);
	}

	List<Resource> findAndCacheByFilter(ResourceFilter resourceFilter,
			boolean firstStop) throws BusinessException {
		List<Resource> resources = new LinkedList<Resource>();
		try {
			for (Resource resource : listResources()) {
				if (resourceFilter != null && resourceFilter.filter(resource)) {
					resources.add(resource);
					if (firstStop) {
						return resources;
					}
				}
				if (hasResourceCache) {
					String code = resource.getStringField(codeField);

					this.resourcesById.put(resource.getStringField("ID"),
							resource);
					this.resourcesByInternalId.put(
							resource.getDoubleField("Internal Id"), resource);

					if (!resourcesByCode.containsKey(code)) {
						resourcesByCode
								.put(code, new LinkedHashSet<Resource>());
					}
					resourcesByCode.get(code).add(resource);
				}
			}
			return resources;
		} catch (DataFormatException e) {
			String message = "Il y a eu un problème de format de données lors du parcours des ressources.";
			Logger.error(message, e);
			throw new TechnicalException(e, message);
		} catch (PSException e) {
			String message = "Il y a eu un problème technique lors du parcours des ressources.";
			Logger.error(message, e);
			throw new TechnicalException(e, message);
		}
	}

	@SuppressWarnings("unchecked")
	private List<Resource> listResources() throws PSException {
		if (addInactiveResources && !this.session.loadingInactiveResources()) {
			this.session.loadInactiveResourcesFromNowOn();
		}
		return (List<Resource>) (usePublishedResources ? this.session
				.getPublishedResourceList() : this.session
				.getWorkingResourceList());
	}

}
