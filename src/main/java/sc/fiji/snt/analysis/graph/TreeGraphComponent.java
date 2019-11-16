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

package sc.fiji.snt.analysis.graph;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collections;

import javax.imageio.ImageIO;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.mxGraphOutline;
import com.mxgraph.swing.handler.mxKeyboardHandler;
import com.mxgraph.swing.handler.mxPanningHandler;
import com.mxgraph.swing.handler.mxRubberband;
import com.mxgraph.util.mxCellRenderer;

import sc.fiji.snt.gui.GuiUtils;

class TreeGraphComponent extends mxGraphComponent {
	private static final long serialVersionUID = 1L;
	private boolean panMode;
	private final TreeGraphAdapter<?, ?> adapter;
	private JCheckBoxMenuItem panMenuItem;
	private File saveDir;

	protected TreeGraphComponent(final TreeGraphAdapter<?, ?> adapter) {
		super(adapter);
		this.adapter = adapter;
		addMouseWheelListener(new ZoomMouseWheelListener());
		addKeyListener(new GraphKeyListener());
		new mxPanningHandler(this);
		new mxKeyboardHandler(this);
		addRuberBandZoom();
		setCenterZoom(true);
		setCenterPage(true);
		setAntiAlias(true);
		setDoubleBuffered(true);
		setConnectable(false);
		setFoldingEnabled(true);
		setKeepSelectionVisibleOnZoom(true);
		setDragEnabled(false);
		assignPopupMenu();
	}

	private void addRuberBandZoom() {
		new mxRubberband(this) {

			@Override
			public boolean isRubberbandTrigger(final MouseEvent e) {
				return e.isAltDown();
			}

			@Override
			public void mouseReleased(final MouseEvent e) {
				// get bounds before they are reset
				final Rectangle rect = super.bounds;

				super.mouseReleased(e);
				if (rect == null)
					return;

				double newScale = 1d;
				final Dimension graphSize = new Dimension(rect.width, rect.height);
				final Dimension viewPortSize = graphComponent.getViewport().getSize();

				final int gw = (int) graphSize.getWidth();
				final int gh = (int) graphSize.getHeight();

				if (gw > 0 && gh > 0) {
					final int w = (int) viewPortSize.getWidth();
					final int h = (int) viewPortSize.getHeight();
					newScale = Math.min((double) w / gw, (double) h / gh);
				}

				// zoom to fit selected area
				graphComponent.zoom(newScale);

				// make selected area visible
				graphComponent.getGraphControl().scrollRectToVisible(new Rectangle((int) (rect.x * newScale),
						(int) (rect.y * newScale), (int) (rect.width * newScale), (int) (rect.height * newScale)));
			}
		};
	}

	private void assignPopupMenu() {
		final JPopupMenu popup = new JPopupMenu();
		setComponentPopupMenu(popup);
		JMenuItem mItem = new JMenuItem("Zoom to Selection (Alt + Click & Drag)");
		mItem.addActionListener(e -> {
			new GuiUtils(this).tempMsg("A Rectangular selection is required.");
		});
		popup.add(mItem);
		mItem = new JMenuItem("Zoom In ([+] or Shift + Mouse Wheel)");
		mItem.addActionListener(e -> zoomIn());
		popup.add(mItem);
		mItem = new JMenuItem("Zoom Out ([-] or Shift + Mouse Wheel)");
		mItem.addActionListener(e -> zoomOut());
		popup.add(mItem);
		mItem = new JMenuItem("Reset Zoom");
		mItem.addActionListener(e -> zoomActual());
		popup.add(mItem);
		popup.addSeparator();

		final JCheckBoxMenuItem jcbmi = new JCheckBoxMenuItem("Label Vertices", adapter.isVertexLabelsEnabled());
		jcbmi.addActionListener(e -> {
			adapter.getModel().beginUpdate();
			adapter.setEnableVertexLabels(jcbmi.isSelected());
			adapter.getModel().endUpdate();
			//HACK: This is the only thing I could come up with to repainting the canvas
			zoomIn(); zoomOut();
		});
		popup.add(jcbmi);
		popup.addSeparator();

		panMenuItem = new JCheckBoxMenuItem("Pan Mode (Shift + Click & Drag)");
		panMenuItem.addActionListener(e -> setPanMode(panMenuItem.isSelected()));
		popup.add(panMenuItem);
		popup.addSeparator();
		final JMenu exportMenu = new JMenu("Save As");
		exportMenu.add(saveAsMenuItem("HTML...", ".html"));
		exportMenu.add(saveAsMenuItem("PNG...", ".png"));
		exportMenu.add(saveAsMenuItem("SVG...", ".svg"));
		popup.add(exportMenu);
		getGraphControl().addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(final MouseEvent e) {
				handleMouseEvent(e);
			}

			@Override
			public void mouseReleased(final MouseEvent e) {
				handleMouseEvent(e);
			}

			private void handleMouseEvent(final MouseEvent e) {
				if (e.isConsumed())
					return;
				if (e.isPopupTrigger()) {
					popup.show(getGraphControl(), e.getX(), e.getY());
				} else {
					setPanMode(e.isShiftDown());
				}
				e.consume();
			}
		});
	}

	private JMenuItem saveAsMenuItem(final String label, final String extension) {
		final JMenuItem menuItem = new JMenuItem(label);
		menuItem.addActionListener(e -> export(extension));
		return menuItem;
	}

	private void export(final String extension) {
		final GuiUtils guiUtils = new GuiUtils(this.getParent());
		final File file = new File(getSaveDir(), "exported-graph" + extension);
		final File saveFile = guiUtils.saveFile("Export Graph...", file, Collections.singletonList(extension));
		if (saveFile == null)
			return; // user pressed cancel;
		saveDir = saveFile.getParentFile();
		try {
			switch (extension) {
			case ".png":
				final BufferedImage image = mxCellRenderer.createBufferedImage(adapter, null, 1, getBackground(), true,
						null);
				ImageIO.write(image, "PNG", saveFile);
				break;
			case ".svg":
				final Document svgDoc = mxCellRenderer.createSvgDocument(adapter, null, 1, getBackground(), null);
				exportDocument(svgDoc, saveFile);
				break;
			case ".html":
				final Document htmlDoc = mxCellRenderer.createHtmlDocument(adapter, null, 1, getBackground(), null);
				exportDocument(htmlDoc, saveFile);
				break;
			default:
				throw new IllegalArgumentException("Unrecognized extension");
			}
			guiUtils.tempMsg(file.getAbsolutePath() + " saved");

		} catch (IOException | TransformerException e) {
			guiUtils.error("An exception occured while saving file. See Console for details");
			e.printStackTrace();
		}
	}

	private File getSaveDir() {
		if (saveDir == null)
			return new File(System.getProperty("user.home"));
		return saveDir;
	}

	private void exportDocument(final Document doc, final File file) throws TransformerException {
		final Transformer transformer = TransformerFactory.newInstance().newTransformer();
		final Result output = new StreamResult(file);
		final Source input = new DOMSource(doc);
		transformer.transform(input, output);
	}

	private void setPanMode(final boolean panMode) {
		this.panMode = panMode || panMenuItem.isSelected();
	}

	@Override
	public boolean isPanningEvent(final MouseEvent event) {
		return panMode;
	}

	class ZoomMouseWheelListener implements MouseWheelListener {
		@Override
		public void mouseWheelMoved(final MouseWheelEvent e) {
			if (e.getSource() instanceof mxGraphOutline || e.isShiftDown()) {
				if (e.getWheelRotation() < 0) {
					TreeGraphComponent.this.zoomIn();
				} else {
					TreeGraphComponent.this.zoomOut();
				}

			}
		}
	};

	class GraphKeyListener extends KeyAdapter {

		@Override
		public void keyPressed(final KeyEvent e) {
			final int keyCode = e.getKeyCode();
			if (keyCode == KeyEvent.VK_PLUS || keyCode == KeyEvent.VK_EQUALS) {
				TreeGraphComponent.this.zoomIn();
			} else if (keyCode == KeyEvent.VK_MINUS) {
				TreeGraphComponent.this.zoomOut();
			}
		}
	};
}