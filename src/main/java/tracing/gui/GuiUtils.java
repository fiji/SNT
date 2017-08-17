
package tracing.gui;

import java.io.File;

import org.scijava.Context;
import org.scijava.plugin.Parameter;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.OptionType;
import org.scijava.ui.DialogPrompt.Result;
import org.scijava.ui.UIService;
import org.scijava.widget.FileWidget;

import tracing.SNT;

public class GuiUtils {

	@Parameter
	private UIService uiService;

	private final String VERSION;

	public GuiUtils(final Context context) {
		context.inject(this);
		VERSION = "SNT v" + SNT.VERSION;
	}

	public void error(final String message, final String title) {
		uiService.showDialog(message, (title == null) ? VERSION + " Error" : title,
			MessageType.ERROR_MESSAGE);
	}

	public void infoMsg(final String message, final String title) {
		uiService.showDialog(message, (title == null) ? VERSION : title,
			MessageType.INFORMATION_MESSAGE);
	}

	public Result yesNoPrompt(final String message, final String title) {
		return uiService.showDialog(message, (title == null) ? VERSION : title,
			MessageType.QUESTION_MESSAGE, OptionType.YES_NO_OPTION);
	}

	public Result yesNoCancelPrompt(final String message, final String title) {
		return uiService.showDialog(message, (title == null) ? VERSION : title,
			MessageType.QUESTION_MESSAGE, OptionType.YES_NO_CANCEL_OPTION);
	}

	public File openFile(final String title, final File initialChoice) {
		return uiService.chooseFile(title, null, FileWidget.OPEN_STYLE);
	}

}
