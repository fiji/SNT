
package tracing.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.MenuSelectionManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

/**
 * This class generates a simplified color widget. It is based on Gerald Bauer's
 * code released under GPL2
 * (http://www.java2s.com/Code/Java/Swing-JFC/ColorMenu.htm)
 */
public class ColorMenu extends JMenu {

	private static final long serialVersionUID = 1L;
	private final Map<Color, ColorPane> _colorPanes;
	private ColorPane _selectedColorPane;
	private final Border _activeBorder;
	private final Border _selectedBorder;
	private final Border _unselectedBorder;

	public ColorMenu(final String name) {
		super(name);

		_unselectedBorder = new CompoundBorder(new MatteBorder(2, 2, 2, 2,
			getBackground()), new MatteBorder(1, 1, 1, 1, getForeground()));

		_selectedBorder = new CompoundBorder(new MatteBorder(1, 1, 1, 1,
			getBackground()), new MatteBorder(2, 2, 2, 2, getForeground()));

		_activeBorder = new CompoundBorder(new MatteBorder(2, 2, 2, 2,
			getForeground()), new MatteBorder(1, 1, 1, 1, getBackground()));

		final Color[] hues = new Color[] { Color.RED, Color.GREEN, Color.BLUE,
			Color.MAGENTA, Color.CYAN, Color.YELLOW, Color.ORANGE };
		final float[] colorRamp = new float[] { .75f, .5f, .3f };
		final float[] grayRamp = new float[] { 1, .86f, .71f, .57f, .43f, .29f, 0 };

		final JPanel p = new JPanel();
		p.setBackground(getBackground());
		p.setBorder(new EmptyBorder(5, 5, 5, 5));
		p.setLayout(new GridLayout(8, 8));
		_colorPanes = new HashMap<>();

		final List<Color> colors = new ArrayList<>();
		for (final Color h : hues) {
			colors.add(h);
			final float[] hsbVals = Color.RGBtoHSB(h.getRed(), h.getGreen(), h
				.getBlue(), null);
			for (final float s : colorRamp) { // lighter colors
				final Color color = Color.getHSBColor(hsbVals[0], s * hsbVals[1],
					hsbVals[2]);
				colors.add(color);
			}
			for (final float s : colorRamp) { // darker colors
				final Color color = Color.getHSBColor(hsbVals[0], hsbVals[1], s *
					hsbVals[2]);
				colors.add(color);
			}
		}
		for (final float s : grayRamp) {
			final Color color = Color.getHSBColor(0, 0, s);
			colors.add(color);
		}
		for (final Color color : colors) {
			final ColorPane colorPane = new ColorPane(color);
			p.add(colorPane);
			_colorPanes.put(color, colorPane);
		}
		add(p);

		// Build the custom color panel
		final JPanel cPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		cPanel.setBackground(getBackground());
		final ColorPane customColorPane = new ColorPane(getBackground());
		cPanel.add(customColorPane);
		_colorPanes.put(getBackground(), customColorPane);
		final JButton promptButton = new JButton("Other...");
		promptButton.setMargin(new Insets(0, 0, 0, 0));
		promptButton.setContentAreaFilled(false);
		promptButton.setBorderPainted(false);
		promptButton.setOpaque(false);
		promptButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				final Color c = new GuiUtils(getTopLevelAncestor()).getColor(
					"New Color", getColor());
				customColorPane.setColor(c);
				setColor(c);
				doSelection();
			}
		});
		cPanel.add(promptButton);
		add(cPanel);
		addSeparator();
	}

	public void setColor(final Color c) {
		final Object obj = _colorPanes.get(c);
		if (obj == null) return;
		if (_selectedColorPane != null) _selectedColorPane.setSelected(false);
		_selectedColorPane = (ColorPane) obj;
		_selectedColorPane.setSelected(true);
	}

	public Color getColor() {
		if (_selectedColorPane == null) return null;
		return _selectedColorPane.getColor();
	}

	public void doSelection() {
		fireActionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
			getActionCommand()));
	}

	private class ColorPane extends JPanel implements MouseListener {

		private static final long serialVersionUID = 1L;
		private Color _color;
		private boolean _isSelected;

		public ColorPane(final Color color) {
			setColor(color);
			setBorder(_unselectedBorder);
			addMouseListener(this);
		}

		public void setColor(final Color color) {
			_color = color;
			setBackground(color);
			final String msg = "RGB: " + color.getRed() + ", " + color.getGreen() +
				", " + color.getBlue();
			setToolTipText(msg);
		}

		public void setSelected(final boolean isSelected) {
			_isSelected = isSelected;
			if (_isSelected) setBorder(_selectedBorder);
			else setBorder(_unselectedBorder);
		}

		public Color getColor() {
			return _color;
		}

		@Override
		public Dimension getMaximumSize() {
			return getPreferredSize();
		}

		@Override
		public Dimension getMinimumSize() {
			return getPreferredSize();
		}

		@Override
		public Dimension getPreferredSize() {
			return new Dimension(15, 15);
		}

		@Override
		public void mouseClicked(final MouseEvent ev) {}

		@Override
		public void mouseEntered(final MouseEvent ev) {
			setBorder(_activeBorder);
		}

		@Override
		public void mouseExited(final MouseEvent ev) {
			setBorder(_unselectedBorder);
		}

		@Override
		public void mousePressed(final MouseEvent ev) {}

		@Override
		public void mouseReleased(final MouseEvent ev) {
			setColor(_color);
			MenuSelectionManager.defaultManager().clearSelectedPath();
			doSelection();
		}
	}

	/** IDE debug method */
	public static void main(final String[] args) {
		final javax.swing.JFrame f = new javax.swing.JFrame();
		final javax.swing.JMenuBar menuBar = new javax.swing.JMenuBar();
		final ColorMenu menu = new ColorMenu("Test");
		menuBar.add(menu);
		f.setJMenuBar(menuBar);
		f.setVisible(true);
		menuBar.setVisible(true);
		menu.setVisible(true);
	}

}
