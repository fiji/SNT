/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2018 Fiji developers.
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

package tracing.gui;

import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * A factory for {@link FADerivedIcon}s presets.
 * 
 * @author Tiago Ferreira
 *
 */
public class IconFactory {

	public enum GLYPH {
		CHART('\uf080', false), //
		CIRCLE('\uf192', false), //
		CODE('\uf120', true), //
		COLOR('\uf53f', true), //
		COLOR2('\uf5c3', true), //
		CROSSHAIR('\uf05b', true), //
		DELETE('\uf55a', true), //
		EXPLORE('\uf610', true), //
		EXPORT('\uf56f', true), //
		FILL('\uf575', true), //
		ID('\uf2c1', false), //
		IMPORT('\uf56f', true), //
		LINK('\uf0c1', true), //
		PEN('\uf303', true), //
		RULER('\uf546', true), //
		SAVE('\uf0c7', false), //
		TABLE('\uf0ce', true), //
		TAG('\uf02b', true), //
		TEXT('\uf031', true), //
		TOOL('\uf0ad', true), //
		TRASH('\uf2ed', false), //
		UNLINK('\uf127', true), //
		WINDOWS('\uf2d2', false);

		private final char id;
		private final boolean solid;

		GLYPH(final char id, final boolean solid) {
			this.id = id;
			this.solid = solid;
		}

	}

	public static Icon getButtonIcon(final GLYPH entry) {
		return new FADerivedIcon(entry.id, UIManager.getFont("Button.font").getSize() * 0.9f,
				UIManager.getColor("Button.foreground"), entry.solid);
	}

	public static Icon getMenuBarIcon(final GLYPH entry) {
		return new FADerivedIcon(entry.id, UIManager.getFont("MenuBar.font").getSize() * 0.8f,
				UIManager.getColor("MenuBar.foreground"), entry.solid);
	}

	public static Icon getMenuIcon(final GLYPH entry) {
		return new FADerivedIcon(entry.id, UIManager.getFont("MenuItem.font").getSize() * 0.8f,
				UIManager.getColor("MenuItem.foreground"), entry.solid);
	}

}