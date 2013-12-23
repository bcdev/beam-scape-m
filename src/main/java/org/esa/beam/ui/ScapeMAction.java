package org.esa.beam.ui;

import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.operator.ScapeMOp;
import org.esa.beam.visat.actions.AbstractVisatAction;

import java.awt.*;

/**
 * SCAPE-M Action class for VISAT
 *
 * @author Tonio Fincke, Olaf Danne
 */
public class ScapeMAction extends AbstractVisatAction {

    private DefaultSingleTargetProductDialog dialog;

    @Override
    public void actionPerformed(CommandEvent event) {

        final String helpId = event.getCommand().getHelpId();
        if (dialog == null) {
            dialog = new DefaultSingleTargetProductDialog(
                    "beam.scapeM",
                    getAppContext(),
                    "SCAPE-M Atmospheric Correction - v" + ScapeMOp.VERSION,
//                    "ScapeMProcessorPlugIn");
                    helpId);
            dialog.setTargetProductNameSuffix("_SCAPEM");
            dialog.getJDialog().getContentPane().setPreferredSize(new Dimension(500, 400));
        }
        dialog.show();
    }

}
