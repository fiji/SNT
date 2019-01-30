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

package tracing.gui;

import com.jidesoft.swing.Searchable;
import com.jidesoft.swing.SearchableBar;
import com.jidesoft.swing.WholeWordsSupport;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

import tracing.SNT;
import tracing.gui.IconFactory.GLYPH;

/**
 * Implements a SearchableBar following SNT's UI.
 *
 * @author Tiago Ferreira
 */
public class SNTSearchableBar extends SearchableBar {

	private static final long serialVersionUID = 1L;

	protected AbstractButton _extraButton;
	protected boolean containsCheckboxes;
	protected static int buttonHeight;
	protected static float iconHeight;
	private int buttonCount;

	private String statusLabelPlaceholder;

	static {
		buttonHeight = (int) new JComboBox<String>().getPreferredSize().getHeight();
		iconHeight = UIManager.getFont("Label.font").getSize();
	}

	public SNTSearchableBar(final Searchable searchable) {
		super(searchable, true);
		init();
		searchable.setCaseSensitive(false);
		searchable.setFromStart(false);
		searchable.setRepeats(true);
		searchable.setCountMatch(true);
		setShowMatchCount(true);
		setBorderPainted(false);
		setBorder(BorderFactory.createEmptyBorder());
		setMismatchForeground(Color.RED);
		setMaxHistoryLength(10);
		setHighlightAll(false); // TODO: update to 3.6.19 see bugfix
		// https://github.com/jidesoft/jide-oss/commit/149bd6a53846a973dfbb589fffcc82abbc49610b
	}

	private void init() {
		createComboBox();
		_leadingLabel = new JLabel();
		if (getMaxHistoryLength() == 0) {
			_leadingLabel.setLabelFor(_textField);
			_textField.setVisible(true);
			_comboBox.setVisible(false);
		}
		else {
			_leadingLabel.setLabelFor(_comboBox);
			_comboBox.setVisible(true);
			_textField.setVisible(false);
		}
		setStatusLabelPlaceholder("");
	}

	public void setStatusLabelPlaceholder(final String placeholder) {
		statusLabelPlaceholder = placeholder;
	}

	@Override
	protected void installComponents() {
		final JPanel mainPanel = leftAlignedPanel();
		final JPanel buttonPanel = new JPanel();
		if ((getVisibleButtons() & SHOW_CLOSE) != 0) {
			mainPanel.add(_closeButton);
		}
		mainPanel.add(_comboBox);
		if ((getVisibleButtons() & SHOW_NAVIGATION) != 0) {
			addButton(buttonPanel, _findNextButton);
			addButton(buttonPanel, _findPrevButton);
		}
		if ((getVisibleButtons() & SHOW_HIGHLIGHTS) != 0) {
			addButton(buttonPanel, _highlightsButton);
		}
		if ((getVisibleButtons() & SHOW_MATCHCASE) != 0) {
			addButton(buttonPanel, _matchCaseCheckBox);
		}
		if ((getVisibleButtons() & SHOW_WHOLE_WORDS) != 0 &&
			getSearchable() instanceof WholeWordsSupport)
		{
			addButton(buttonPanel, _wholeWordsCheckBox);
		}
		if ((getVisibleButtons() & SHOW_REPEATS) != 0) {
			addButton(buttonPanel, _repeatCheckBox);
		}
		if (_extraButton != null) {
			addButton(buttonPanel, _extraButton);
		}
		if (buttonCount > 0) {
			if (!containsCheckboxes) buttonPanel.setLayout(new GridLayout(1,
				buttonCount));
			mainPanel.add(buttonPanel);
		}
		add(mainPanel);
		if ((getVisibleButtons() & SHOW_STATUS) != 0) {
			setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
			add(statusPanel());
		}
	}

	private void addButton(final JPanel panel, final AbstractButton button) {
		containsCheckboxes = button instanceof JCheckBox;
		panel.add(button);
		buttonCount++;
	}

	private JPanel leftAlignedPanel() {
		final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private JPanel statusPanel() {
		final JPanel statusPanel = leftAlignedPanel();
		_statusLabel = new JLabel(SNT.getReadableVersion());
		statusPanel.add(_statusLabel);
		_statusLabel.addPropertyChangeListener("text", evt -> {
			final String text = _statusLabel.getText();
			if (text == null || text.isEmpty()) _statusLabel.setText(
				statusLabelPlaceholder);
		});
		return statusPanel;
	}

	public void setStatus(final String text) {
		super._statusLabel.setText(text);
	}

	@Override
	protected AbstractButton createFindPrevButton(
		final AbstractAction findPrevAction)
	{
		final AbstractButton button = super.createFindPrevButton(findPrevAction);
		formatButton(button, GLYPH.PREVIOUS);
		return button;
	}

	@Override
	protected AbstractButton createMatchCaseButton() {
		final AbstractButton button = super.createMatchCaseButton();
		button.setText("Aa");
		button.setMnemonic('a');
		button.setToolTipText("Match case");
		return button;
	}

	@Override
	protected AbstractButton createFindNextButton(
		final AbstractAction findNextAction)
	{
		final AbstractButton button = super.createFindNextButton(findNextAction);
		formatButton(button, GLYPH.NEXT);
		return button;
	}

	@Override
	protected AbstractButton createCloseButton(final AbstractAction closeAction) {
		final AbstractButton button = super.createCloseButton(closeAction);
		formatButton(button, GLYPH.TIMES);
		return button;
	}

	@Override
	protected AbstractButton createHighlightButton() {
		final AbstractButton button = super.createHighlightButton();
		formatButton(button, GLYPH.BULB);
		Color selectionColor = UIManager.getColor("Tree.selectionBackground");
		if (selectionColor == null) selectionColor = Color.RED;
		final Icon selectIcon = IconFactory.getIcon(GLYPH.BULB, iconHeight,
			selectionColor);
		button.setSelectedIcon(selectIcon);
		button.setRolloverSelectedIcon(selectIcon);
		return button;
	}

	protected void formatButton(final AbstractButton button, final GLYPH glyph) {
		button.setPreferredSize(new Dimension(buttonHeight, buttonHeight));
		IconFactory.applyIcon(button, iconHeight, glyph);
	}
}
