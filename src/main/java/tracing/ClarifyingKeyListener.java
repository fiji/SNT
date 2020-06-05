/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2020 Fiji developers.
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

package tracing;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import ij.IJ;

/**
 * There have been problems on Mac OS with people trying to start the Sholl
 * analysis interface, but while the focus isn't on the image window. This is
 * just a key listener to detect such attempts and suggest to people what might
 * be wrong if they type Shift with Control-A or Alt-A in the wrong window.
 * (This will be added to all the Wrong Windows.)
 */

public class ClarifyingKeyListener implements KeyListener, ContainerListener {

	/*
	 * Grabbing all key presses in a dialog window isn't trivial, but the
	 * technique suggested here works fine:
	 * http://www.javaworld.com/javaworld/javatips/jw-javatip69.html
	 */

	public void addKeyAndContainerListenerRecursively(final Component c) {
		c.addKeyListener(this);
		if (c instanceof Container) {
			final Container container = (Container) c;
			container.addContainerListener(this);
			final Component[] children = container.getComponents();
			for (int i = 0; i < children.length; i++) {
				addKeyAndContainerListenerRecursively(children[i]);
			}
		}
	}

	private void removeKeyAndContainerListenerRecursively(final Component c) {
		c.removeKeyListener(this);
		if (c instanceof Container) {
			final Container container = (Container) c;
			container.removeContainerListener(this);
			final Component[] children = container.getComponents();
			for (int i = 0; i < children.length; i++) {
				removeKeyAndContainerListenerRecursively(children[i]);
			}
		}
	}

	@Override
	public void componentAdded(final ContainerEvent e) {
		addKeyAndContainerListenerRecursively(e.getChild());
	}

	@Override
	public void componentRemoved(final ContainerEvent e) {
		removeKeyAndContainerListenerRecursively(e.getChild());
	}

	@Override
	public void keyPressed(final KeyEvent e) {

		final int keyCode = e.getKeyCode();

		if (e.isShiftDown() && (e.isControlDown() || e.isAltDown()) && (keyCode == KeyEvent.VK_A)) {
			IJ.error("You seem to be trying to start Sholl analysis, but the focus is on the wrong window.\n"
					+ "Bring the (2D) image window to the foreground and try again.");
			e.consume();
		}
	}

	@Override
	public void keyReleased(final KeyEvent e) {
	}

	@Override
	public void keyTyped(final KeyEvent e) {
	}

}
