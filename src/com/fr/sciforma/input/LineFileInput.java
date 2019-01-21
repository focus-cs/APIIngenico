/*
 * © 2008 Sciforma. Tous droits réservés. 
 */
package com.fr.sciforma.input;

import java.util.List;

/**
 * interface de lecture de fichier élément par élément
 * 
 * @param <LINE>
 *            élément
 */
public interface LineFileInput<LINE> {

	/**
	 * tout lire
	 * 
	 * @return list representant tout le fichier
	 */
	List<LINE> readAll();

}
