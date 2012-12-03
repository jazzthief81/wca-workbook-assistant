package org.worldcubeassociation.ui;

import org.worldcubeassociation.WorkbookUploaderEnv;
import org.worldcubeassociation.workbook.JSONGenerator;
import org.worldcubeassociation.workbook.MatchedSheet;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * @author Lars Vandenbergh
 */
public class GenerateJSONAction extends AbstractAction implements PropertyChangeListener {

    private WorkbookUploaderEnv fEnv;
    private JDialog fDialog;
    private JTextArea fTextArea;

    public GenerateJSONAction(WorkbookUploaderEnv aEnv) {
        super("Generate JSON...");
        fEnv = aEnv;
        fEnv.addPropertyChangeListener(this);

        initUI();
        updateEnabledState();
    }

    private void updateEnabledState() {
        setEnabled(fEnv.getMatchedWorkbook() != null);
    }

    private void initUI() {
        fDialog = new JDialog(fEnv.getTopLevelComponent(), "Generate JSON", Dialog.ModalityType.APPLICATION_MODAL);
        fDialog.getContentPane().setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();

        c.insets.top = 4;
        c.insets.right = 4;
        c.insets.left = 4;
        c.insets.bottom = 4;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        c.gridx = GridBagConstraints.REMAINDER;
        fTextArea = new JTextArea(50, 150);
        fTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        fTextArea.setLineWrap(true);
        fTextArea.setWrapStyleWord(true);
        fTextArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(fTextArea,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        fDialog.getContentPane().add(scrollPane, c);

        c.weighty = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST;
        fDialog.getContentPane().add(new JButton(new CopyAction()), c);

        fDialog.pack();
    }

    @Override
    public void actionPerformed(ActionEvent aActionEvent) {
        List<MatchedSheet> sheets = fEnv.getMatchedWorkbook().sheets();
        for (MatchedSheet sheet : sheets) {
            if (!sheet.getValidationErrors().isEmpty()) {
                JOptionPane.showMessageDialog(fEnv.getTopLevelComponent(),
                        "Some sheets still contain validation errors and will be skipped!",
                        "Generate JSON",
                        JOptionPane.WARNING_MESSAGE);
                break;
            }
        }

        try {
            String scripts = JSONGenerator.generateJSON(fEnv.getMatchedWorkbook());
            fTextArea.setText(scripts);

            fDialog.setLocationRelativeTo(fEnv.getTopLevelComponent());
            fDialog.setVisible(true);
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(fEnv.getTopLevelComponent(),
                    "An unexpected validation error occurred in one of the sheets!",
                    "Generate JSON",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent aPropertyChangeEvent) {
        if (WorkbookUploaderEnv.MATCHED_WORKBOOK_PROPERTY.equals(aPropertyChangeEvent.getPropertyName())) {
            updateEnabledState();
        }
    }

    private class CopyAction extends AbstractAction {

        private CopyAction() {
            super("Copy");
        }

        @Override
        public void actionPerformed(ActionEvent aActionEvent) {
            Toolkit.getDefaultToolkit().
                    getSystemClipboard().
                    setContents(new StringSelection(fTextArea.getText()), null);
        }

    }

}
