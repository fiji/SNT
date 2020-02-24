/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2019 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package sc.fiji.snt.annotation;

import sc.fiji.snt.viewer.OBJMesh;

/**
 * Classes extending this interface implement a neuropil label/annotation.
 *
 * @author Tiago Ferreira
 */
public interface BrainAnnotation {

	/** @return the compartment's unique id */
	public int id();

	/** @return the compartment's name */
	public String name();

	/** @return the compartment's acronym */
	public String acronym();

	/** @return the compartment's alias(es) */
	public String[] aliases();

	/** @return the mesh associated with this compartment */
	public OBJMesh getMesh();

	/** @return assesses if {@annotation} is contained by this compartment */
	public boolean contains(BrainAnnotation annotation);

}
