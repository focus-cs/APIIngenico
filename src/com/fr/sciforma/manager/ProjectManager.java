/*
 * © 2008 Sciforma. Tous droits réservés. 
 */
package com.fr.sciforma.manager;

import java.util.List;

import com.sciforma.psnext.api.PSException;
import com.sciforma.psnext.api.Project;

import fr.sciforma.psconnect.exception.TechnicalException;

/**
 * interface pour la recherche de projets
 * 
 * @author pyard@sciforma.fr
 * 
 */
public interface ProjectManager {

	/**
	 * recherche d'un projet par son identifiant interne
	 * 
	 * @param internalId
	 * @return
	 * @throws TechnicalException
	 */
	Project findProjectByInternalId(Long internalId) throws TechnicalException;

	/**
	 * rechercher d'un projet par son identifiant fonctionnel
	 * 
	 * @param ID
	 * @return
	 * @throws TechnicalException
	 */
	Project findProjectById(String id) throws TechnicalException;

	/**
	 * filtre des projets
	 * 
	 * @author pyard@sciforma.fr
	 * 
	 */
	public interface ProjectFilter {
		boolean filter(Project project) throws PSException;
	}

	/**
	 * Recherche avec critères
	 * 
	 * @param projectCriteria
	 * @return
	 * @throws TechnicalException
	 */
	List<Project> findProjectsByCriteria(ProjectFilter projectCriteria)
			throws TechnicalException;

	/**
	 * recherche du non project
	 * 
	 * @return
	 * @throws TechnicalException
	 */
	Project openNonProject() throws TechnicalException;

	/**
	 * tentative de suppression logique d'un projet
	 * 
	 * @param id
	 * @throws TechnicalException
	 */
	void deleteProject(String id) throws TechnicalException;

}
