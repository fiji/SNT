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
 * 
http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package sc.fiji.snt.analysis;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import org.scijava.table.DefaultGenericTable;

import sc.fiji.snt.SNTUtils;

/**
 * Extension of {@code DefaultGenericTable} with minor scripting conveniences.
 *
 * @author Tiago Ferreira
 */
public class SNTTable extends DefaultGenericTable {

	private static final long serialVersionUID = 1L;
	private boolean hasUnsavedData;

	public void fillEmptyCells(final Object value) {
		for (int col = 0; col < getColumnCount(); ++col) {
			for (int row = 0; row < getRowCount(); ++row) {
				if (get(col, row) == null) {
					super.set(col, row, value);
				}
			}
		}
	}

	public boolean hasUnsavedData() {
		return getRowCount() > 0 && hasUnsavedData;
	}

	public void set(final String colHeader, final int row, final Object value) {
		set(getCol(colHeader), row, value);
	}

	@Override
	public void set(final int col, final int row, final Object value) {
		super.set(col, row, value);
		hasUnsavedData = true;
	}

	private int getCol(final String header) {
		int idx = getColumnIndex(header);
		if (idx == -1) {
			appendColumn(header);
			idx = getColumnCount() - 1;
		}
		return idx;
	}

	public boolean save(final String filePath) {
		if (filePath == null || filePath.trim().isEmpty()) {
			throw new IllegalArgumentException("filePath is not valid");
		}
		try {
			final String fPath = (filePath.toLowerCase().endsWith(".csv")) ? filePath : filePath + ".csv";
			save(new File(fPath));
			return true;
		} catch (final IOException ignored) {
			return false;
		}
	}

	public void save(final File outputFile) throws IOException {
		final String sep = ",";
		final PrintWriter pw = new PrintWriter(
				new OutputStreamWriter(new FileOutputStream(outputFile.getAbsolutePath()), StandardCharsets.UTF_8));
		final int columns = getColumnCount();
		final int rows = getRowCount();

		// Print a column header to hold row headers
		SNTUtils.csvQuoteAndPrint(pw, "Description");
		pw.print(sep);
		for (int col = 0; col < columns; ++col) {
			SNTUtils.csvQuoteAndPrint(pw, getColumnHeader(col));
			if (col < (columns - 1))
				pw.print(sep);
		}
		pw.print("\r\n");
		for (int row = 0; row < rows; row++) {
			SNTUtils.csvQuoteAndPrint(pw, getRowHeader(row));
			pw.print(sep);
			for (int col = 0; col < columns; col++) {
				SNTUtils.csvQuoteAndPrint(pw, get(col, row));
				if (col < (columns - 1))
					pw.print(sep);
			}
			pw.print("\r\n");
		}
		pw.close();
		hasUnsavedData = false;
	}

}
