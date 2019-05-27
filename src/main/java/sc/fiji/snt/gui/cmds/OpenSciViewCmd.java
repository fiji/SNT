package sc.fiji.snt.gui.cmds;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.snt.SNTService;
import sc.iview.SciView;

/**
 * Command for opening SciView in SNT
 *
 * @author Kyle Harrington
 */
@Plugin(type = Command.class, visible = false)
public class OpenSciViewCmd implements Command {
    @Parameter
    private SciView sciView;

    @Parameter
	private SNTService sntService;

    @Override
    public void run() {
        System.out.println("SetSciView in OpenSciViewCmd");
        sntService.setSciView(sciView);
        sciView.getFloor().setVisible(false);
    }
}
