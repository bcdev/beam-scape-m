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

    @Override
    public void actionPerformed(CommandEvent event) {
        final String version = ScapeMOp.VERSION;
        final String helpId = event.getCommand().getHelpId();
        final DefaultSingleTargetProductDialog productDialog = new DefaultSingleTargetProductDialog(
                "beam.scapeM", getAppContext(),
                "SCAPE-M Atmospheric Correction - v" + version, helpId);
        productDialog.setTargetProductNameSuffix("_SCAPEM");
        productDialog.getJDialog().getContentPane().setPreferredSize(new Dimension(500, 400));
        productDialog.show();
    }

}
