/*
 * © 2008 Sciforma. Tous droits réservés. 
 */
package com.fr.sciforma.manager;

import java.util.List;

import com.sciforma.psnext.api.PSException;
import com.sciforma.psnext.api.Resource;

import fr.sciforma.psconnect.exception.BusinessException;
import fr.sciforma.psconnect.exception.TechnicalException;

/**
 * Manager pour l'accès aux resources
 */
public interface ResourceManager {

	/**
	 * nom du champ par défaut pour les recherches sur les ressources
	 */
	String RESOURCE_CODE = "Email Address 1";

	/**
	 * find a resource by this ID
	 * 
	 * @param id
	 *            resource ID
	 * @return PSNExt resource
	 * @throws BusinessException
	 * @throws TechnicalException
	 */
	Resource findResourceById(String id) throws BusinessException,
			TechnicalException;

	/**
	 * find a resource by a code
	 * 
	 * @param id
	 *            resource ID
	 * @return PSNExt resource
	 * @throws BusinessException
	 * @throws TechnicalException
	 */
	Resource findResourceByCode(String Code) throws BusinessException,
			TechnicalException;

	/**
	 * filtre ressource
	 */
	public interface ResourceFilter {

		boolean filter(Resource resource) throws PSException;
	}

	/**
	 * find a resource by Criteria
	 * 
	 * @param id
	 *            resource ID
	 * @return PSNExt resource
	 * @throws BusinessException
	 * @throws TechnicalException
	 */
	List<Resource> findResourceByCriteria(ResourceFilter filter)
			throws BusinessException, TechnicalException;
}
