/*
 * © 2009 Sciforma. Tous droits réservés. 
 */
package com.fr.sciforma.manager;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.sciforma.psnext.api.AccessException;
import com.sciforma.psnext.api.InvalidDataException;
import com.sciforma.psnext.api.PSException;
import com.sciforma.psnext.api.Project;
import com.sciforma.psnext.api.Session;

import fr.sciforma.psconnect.exception.BusinessException;
import fr.sciforma.psconnect.exception.TechnicalException;
import org.pmw.tinylog.Logger;

/**
 * implementation du manager de projet
 */
public class ProjectManagerImpl implements ProjectManager {

    private Map<String, Project> projectsByID = new HashMap<String, Project>();

    private Map<Double, Project> projectsByInternalId = new HashMap<Double, Project>();

    private Project nonProject;

    private boolean hasProjectCache = true;

    private boolean defaultFirstStop = false;

    private boolean useSkeleton = false;

    private boolean useInactive = false;

    private int version = Project.VERSION_PUBLISHED;

    private int access = Project.READWRITE_ACCESS;

    private Session session;

    public ProjectManagerImpl(Session session) {
        this.session = session;
    }

    /**
     * @param version the version to set
     */
    public void setVersion(int version) {
        this.version = version;
    }

    /**
     * fluent setter
     *
     * @param versionPublished
     * @return this
     */
    public ProjectManagerImpl withVersion(int version) {
        setVersion(version);
        return this;
    }

    /**
     * @param access the access to set
     */
    public void setAccess(int access) {
        this.access = access;
    }

    /**
     * fluent setter access
     *
     * @param access the access to set
     */
    public ProjectManagerImpl withAccess(int access) {
        this.access = access;
        return this;
    }

    /**
     * @param useSkeleton the useSkeleton to set
     */
    public void setUseSkeleton(boolean useSkeleton) {
        this.useSkeleton = useSkeleton;
    }

    /**
     * fluent setter useSkeleton
     *
     * @param useSkeleton
     * @return
     */
    public ProjectManagerImpl withUseSkeleton(boolean useSkeleton) {
        this.useSkeleton = useSkeleton;
        return this;
    }

    /**
     * @param hasProjectCache the hasProjectCache to set
     */
    public void setHasProjectCache(boolean hasProjectCache) {
        this.hasProjectCache = hasProjectCache;
    }

    /**
     * fluent setter
     *
     * @param hasProjectCache the hasProjectCache to set
     * @return this
     */
    public ProjectManagerImpl withHasProjectCache(boolean hasProjectCache) {
        this.hasProjectCache = hasProjectCache;
        return this;
    }

    /**
     * @param defaultFirstStop immediaty stop in find
     */
    public void setDefaultFirstStop(boolean defaultFirstStop) {
        this.defaultFirstStop = defaultFirstStop;
    }

    /**
     * fluent setter
     *
     * @param defaultFirstStop immediaty stop in find
     * @return
     */
    public ProjectManagerImpl withDefaultFirstStop(boolean defaultFirstStop) {
        this.defaultFirstStop = defaultFirstStop;
        return this;
    }

    private void putInCache(String id, Double internalId, Project project) {
        if (project.isNonProject()) {
            nonProject = project;
        } else {
            projectsByID.put(id, project);
            projectsByInternalId.put(internalId, project);
        }
    }

    public Project findProjectByInternalId(final Long internalId)
            throws TechnicalException {
        if (hasProjectCache) {
            Project project = projectsByInternalId.get(internalId);

            if (project != null) {
                return project;
            }
        }

        List<Project> findAndCacheByFilter = findAndCacheByFilter(
                new ProjectFilter() {
                    public boolean filter(Project project) throws PSException {
                        return internalId.doubleValue() == project.getDoubleField("Internal Id");
                    }
                }, true);

        return findAndCacheByFilter.isEmpty() ? null : findAndCacheByFilter
                .iterator().next();
    }

    public Project findProjectById(final String id) throws TechnicalException {
        System.out.println("id = " + id);
        if (hasProjectCache) {
            Project project = projectsByID.get(id);

            if (project != null) {
                return project;
            }
        }

        List<Project> findAndCacheByFilter = findAndCacheByFilter(
                new ProjectFilter() {
                    public boolean filter(Project project) throws PSException {
                        System.out.println("project = " + project.getStringField("SAP_ID"));
                        return id.equals(project.getStringField("SAP_ID"));
                    }
                }, true);

        return findAndCacheByFilter.isEmpty() ? null : findAndCacheByFilter
                .iterator().next();
    }

    public List<Project> findProjectsByCriteria(ProjectFilter projectFilter)
            throws TechnicalException {
        return findAndCacheByFilter(projectFilter, defaultFirstStop);
    }

    @SuppressWarnings("unchecked")
    List<Project> findAndCacheByFilter(ProjectFilter projectFilter,
            boolean firstStop) {
        List<Project> list = new LinkedList<Project>();
        try {
            for (Project project : (List<Project>) (useSkeleton ? this.session
                    .getSkeletonProjectList(version, access) : this.session
                    .getProjectList(version, access))) {
                try {
                    if (projectFilter.filter(project)) {
                        list.add(project);
                        if (firstStop) {
                            return list;
                        }
                    }
                    if (hasProjectCache) {
                        putInCache(project.getStringField("SAP_ID"),
                                project.getDoubleField("Internal Id"), project);
                    }
                } catch (PSException e) {
                    throw new TechnicalException(e);
                }
            }
            if (this.useInactive) {
                for (Project project : (List<Project>) this.session
                        .getInactiveProjectList(version, access)) {
                    try {
                        if (projectFilter.filter(project)) {
                            list.add(project);
                            if (firstStop) {
                                return list;
                            }
                        }
                        if (hasProjectCache) {
                            putInCache(project.getStringField("SAP_ID"),
                                    project.getDoubleField("Internal Id"),
                                    project);
                        }
                    } catch (PSException e) {
                        throw new TechnicalException(e);
                    }
                }
            }
        } catch (AccessException e) {
            throw new TechnicalException(e,
                    "problème d'accès lors de la recherche de projet <"
                    + projectFilter + ">");
        } catch (InvalidDataException e) {
            throw new TechnicalException(e,
                    "problème de données invalide lors de la recherche de projet <"
                    + projectFilter + ">");
        } catch (PSException e) {
            throw new TechnicalException(e);
        }
        return list;
    }

    public boolean activate(Project project) throws BusinessException,
            TechnicalException {
        try {
            project.activate();
        } catch (PSException e) {
            throw new TechnicalException(e);
        }

        return true;
    }

    public void deleteProject(String id) throws TechnicalException {
        Project project = hasProjectCache ? projectsByID.get(id)
                : findProjectById(id);

		// il n'y a pas de moyen pour supprimer physiquement un projet de PSNext
        // par l'API une suppression logique est réaliser, l'idendiant du projet
        // est modifié et le projet est désactivé
        if (project != null) {
            try {
                project.open(false);
                project.setStringField("ID", "ZZZZZ-" + Math.random() * 1000
                        + "-" + id);
                project.save();
                project.close();
                project.deactivate();
            } catch (PSException e) {
                throw new TechnicalException(e,
                        "impossible de supprimer ce projet");
            }
            Logger.warn("le projet <"
                    + id
                    + "> a été mis en purge, une opération manuelle est nécessaire par l'administrateur pour le supprimer définitivement.");
            if (hasProjectCache) {
                projectsByID.remove(id);
            }
        } else {
            Logger.warn("il n'y a pas de projet <" + id + "> à supprimer.");
        }
    }

    public Project openNonProject() throws TechnicalException {
        return openNonProject(new LinkedHashSet<String>(
                Arrays.asList(new String[]{"hors-projet", "Hors-projet",
                    "Hors-Projet", "Non-Project", "Non-projet",
                    "Non-Projet", "Non-project"})));
    }

    private Project openNonProject(Collection<String> names) {
        if (names.isEmpty()) {
            throw new TechnicalException("activez le suivi hors projet.");
        }

        String name = names.iterator().next();
        Project project;

        try {
            if (this.nonProject == null) {
                project = new Project("", name, Project.VERSION_PUBLISHED);
            } else {
                project = this.nonProject;
            }
            project.open(true);
        } catch (PSException e) {
            names.remove(name);
            return openNonProject(names);
        }

        Logger.debug("Le projet hors-projet est <" + project + ">");

        this.nonProject = project;

        return this.nonProject;
    }

}
