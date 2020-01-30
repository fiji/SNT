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

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

import com.mxgraph.layout.mxCompactTreeLayout;
import com.mxgraph.model.mxGeometry;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.mxGraphOutline;
import com.mxgraph.swing.handler.mxKeyboardHandler;
import com.mxgraph.swing.handler.mxRubberband;
import com.mxgraph.util.mxCellRenderer;
import sc.fiji.snt.gui.GuiUtils;


class TreeGraphComponent extends mxGraphComponent {
	private static final long serialVersionUID = 1L;
	private final TreeGraphAdapter  adapter;
	private final mxCompactTreeLayout layout;
	private final KeyboardHandler keyboardHandler;
	private final JCheckBoxMenuItem panMenuItem;
	private JButton flipButton;
	private File saveDir;

	protected TreeGraphComponent(final TreeGraphAdapter adapter) {
		super(adapter);
		this.adapter = adapter;
		addMouseWheelListener(new ZoomMouseWheelListener());
		setKeepSelectionVisibleOnZoom(true);
		setCenterZoom(true);
		addRubberBandZoom();
		panningHandler = createPanningHandler();
		setPanning(true);
		keyboardHandler = new KeyboardHandler(this);
		setEscapeEnabled(true);

		setAntiAlias(true);
		setDoubleBuffered(true);
		setConnectable(true);
		setFoldingEnabled(true);
		setDragEnabled(false);
		layout = new mxCompactTreeLayout(adapter);
		layout.execute(adapter.getDefaultParent());
		panMenuItem = new JCheckBoxMenuItem("Pan Mode");
	}

	protected Component getJSplitPane() {
		// Default dimensions are exaggerated. Curb them a bit
		setPreferredSize(getPreferredSize());
		assignPopupMenu(this);
		centerGraph();
		requestFocusInWindow();
		return new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, getControlPanel(), this);
	}

	private JComponent getControlPanel() {
		final JPanel buttonPanel = new JPanel(new GridBagLayout());
		final GridBagConstraints gbc = new GridBagConstraints();
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.gridwidth = GridBagConstraints.REMAINDER;

		GuiUtils.addSeparator(buttonPanel, "Navigation:", true, gbc);
		JButton button = new JButton("Zoom In");
		button.setToolTipText("[+] or Shift + Mouse Wheel");
		button.addActionListener(e -> { zoomIn(); });
		buttonPanel.add(button, gbc);
		button = new JButton("Zoom Out");
		button.setToolTipText("[-] or Shift + Mouse Wheel");
		button.addActionListener(e -> { zoomOut(); });
		buttonPanel.add(button, gbc);
		button = new JButton("Reset Zoom");
		button.addActionListener(e -> {zoomActual(); zoomAndCenter();});
		buttonPanel.add(button, gbc);
		button = new JButton("Center");
		button.addActionListener(e -> {centerGraph();});
		buttonPanel.add(button, gbc);
		panMenuItem.addActionListener(e -> getPanningHandler().setEnabled(panMenuItem.isSelected()));
		buttonPanel.add(panMenuItem, gbc);

		GuiUtils.addSeparator(buttonPanel, "Layout:", true, gbc);
		flipButton = new JButton((layout.isHorizontal()?"Vertical":"Horizontal"));
		flipButton.addActionListener(e -> flipGraphToHorizontal(!layout.isHorizontal()));
		buttonPanel.add(flipButton, gbc);
		button = new JButton("Reset");
		button.addActionListener(e -> {
			zoomActual();
			zoomAndCenter();
			flipGraphToHorizontal(true);
		});
		buttonPanel.add(button, gbc);
		final JButton labelsButton = new JButton("Labels");
		final JPopupMenu lPopup = new JPopupMenu();
		final JCheckBox vCheckbox = new JCheckBox("Vertices (Node ID)", adapter.isVertexLabelsEnabled());
		vCheckbox.addActionListener( e -> {
			adapter.setEnableVertexLabels(vCheckbox.isSelected());
		});
		lPopup.add(vCheckbox);
		final JCheckBox eCheckbox = new JCheckBox("Edges (Inter-node distance)", adapter.isEdgeLabelsEnabled());
		eCheckbox.addActionListener( e -> {
			adapter.setEnableEdgeLabels(eCheckbox.isSelected());
		});
		lPopup.add(eCheckbox);
		labelsButton.addMouseListener(new MouseAdapter() {
			public void mousePressed(final MouseEvent e) {
				lPopup.show(labelsButton, e.getX(), e.getY());
			}
		});
		buttonPanel.add(labelsButton, gbc);

		GuiUtils.addSeparator(buttonPanel, "Export:", true, gbc);
		final JButton ioButton = new JButton("Save As");
		final JPopupMenu popup = new JPopupMenu();
		popup.add(saveAsMenuItem("HTML...", ".html"));
		popup.add(saveAsMenuItem("PNG...", ".png"));
		popup.add(saveAsMenuItem("SVG...", ".svg"));
		ioButton.addMouseListener(new MouseAdapter() {
			public void mousePressed(final MouseEvent e) {
				popup.show(ioButton, e.getX(), e.getY());
			}
		});
		buttonPanel.add(ioButton, gbc);
		final JPanel holder = new JPanel(new BorderLayout());
		holder.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
		holder.add(buttonPanel, BorderLayout.CENTER);
		return new JScrollPane(holder);
	}

	@Override
	public void zoomIn() {
		super.zoomIn();
		centerGraph();
	}

	@Override
	public void zoomOut() {
		super.zoomOut();
		centerGraph();
	}

	private void addRubberBandZoom() {
		new mxRubberband(this) {

			@Override
			public void mouseReleased(final MouseEvent e) {
				if (e.isAltDown()) {
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
				} else {
					super.mouseReleased(e);
				}
			}
		};
	}

	private void assignPopupMenu(final JComponent component) {
		final JPopupMenu popup = new JPopupMenu();
		component.setComponentPopupMenu(popup);
		JMenuItem mItem = new JMenuItem("Zoom to Selection (Alt + Click & Drag)");
		mItem.addActionListener(e -> {
			new GuiUtils(this).error("Please draw a rectangular selection while holding \"Alt\".");
		});
		popup.add(mItem);
		mItem = new JMenuItem("Zoom In ([+] or Shift + Mouse Wheel)");
		mItem.addActionListener(e -> zoomIn());
		popup.add(mItem);
		mItem = new JMenuItem("Zoom Out ([-] or Shift + Mouse Wheel)");
		mItem.addActionListener(e -> zoomOut());
		popup.add(mItem);
		mItem = new JMenuItem("Reset Zoom");
		mItem.addActionListener(e -> {zoomActual(); zoomAndCenter(); centerGraph();});
		popup.add(mItem);
		popup.addSeparator();
		mItem = new JMenuItem("Available Shortcuts...");
		mItem.addActionListener(e -> keyboardHandler.displayKeyMap());
		popup.add(mItem);

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
				}
				e.consume();
			}
		});
	}

	@Override
	public Dimension getPreferredSize() {
		final double width = adapter.getGraphBounds().getWidth();
		final double height = adapter.getGraphBounds().getHeight();
		return new Dimension((int)Math.min(600, width), (int)Math.min(400, height));
	}

	private void centerGraph() {
		// https://stackoverflow.com/a/36947526
		final double widthLayout = getLayoutAreaSize().getWidth();
		final double heightLayout = getLayoutAreaSize().getHeight();
		final double width = adapter.getGraphBounds().getWidth();
		final double height = adapter.getGraphBounds().getHeight();
		adapter.getModel().setGeometry(adapter.getDefaultParent(),
				new mxGeometry((widthLayout - width) / 2, (heightLayout - height) / 2, widthLayout, heightLayout));
	}

	private void flipGraphToHorizontal(final boolean horizontal) {
		layout.setHorizontal(horizontal);
		layout.execute(adapter.getDefaultParent());
		centerGraph();
		if (flipButton != null)
			flipButton.setText((layout.isHorizontal())?"Vertical":"Horizontal");
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

	@Override
	public boolean isPanningEvent(final MouseEvent event) {
		return panMenuItem.isSelected();
	}

	private class ZoomMouseWheelListener implements MouseWheelListener {
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

	private class KeyboardHandler extends mxKeyboardHandler {

		public KeyboardHandler(mxGraphComponent graphComponent) {
			super(graphComponent);
		}
		protected InputMap getInputMap(int condition) {
			final InputMap map = super.getInputMap(condition);
			if (condition == JComponent.WHEN_FOCUSED) {
				map.put(KeyStroke.getKeyStroke("EQUALS"), "zoomIn");
				map.put(KeyStroke.getKeyStroke("control EQUALS"), "zoomIn");
				map.put(KeyStroke.getKeyStroke("MINUS"), "zoomOut");
				map.put(KeyStroke.getKeyStroke("control MINUS"), "zoomOut");
			}
			return map;
		}

		private void displayKeyMap() {
			final InputMap inputMap = getInputMap(JComponent.WHEN_FOCUSED);
			final KeyStroke[] keys = inputMap.allKeys();
			final ArrayList<String> lines = new ArrayList<>();
			final String common = "<span style='display:inline-block;width:100px;font-weight:bold'>";
			if (keys != null) {
				for (int i = 0; i < keys.length; i++) {
					final KeyStroke key = keys[i];
					final String keyString = key.toString().replace("pressed", "");
					lines.add(common + keyString + "</span>&nbsp;&nbsp;" + inputMap.get(key));
				}
				Collections.sort(lines);
			}
			GuiUtils.showHTMLDialog("<HTML>" + String.join("<br>", lines), "Dendrogram Viewer Shortcuts");
		}
	}

}