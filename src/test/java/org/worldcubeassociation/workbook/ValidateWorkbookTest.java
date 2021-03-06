package org.worldcubeassociation.workbook;

import org.apache.poi.ss.usermodel.Workbook;
import org.worldcubeassociation.db.Database;
import org.worldcubeassociation.db.WCADatabaseExportDecoder;

import java.io.File;

/**
 * @author Lars Vandenbergh
 */
public class ValidateWorkbookTest extends AbstractWorkbookTest {

    private Database fDatabase;

    public static void main(String[] args) {
        new ValidateWorkbookTest().start(args[0]);
    }

    @Override
    public void start(String aPath) {
        try {
            fDatabase = WCADatabaseExportDecoder.decodeMostRecentExport(null);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        super.start(aPath);
    }

    @Override
    protected void handleFile(File aWorkbookFile) {
        try {
            Workbook workbook = createWorkbook(aWorkbookFile);
            MatchedWorkbook matchedWorkbook = WorkbookMatcher.match(workbook, aWorkbookFile.getAbsolutePath());
            WorkbookValidator.validate(matchedWorkbook, fDatabase, null);

            boolean errors = false;
            for (MatchedSheet matchedSheet : matchedWorkbook.sheets()) {
                if (!matchedSheet.getValidationErrors().isEmpty()) {
                    errors = true;
                }
            }

            if (!errors) {
                System.out.println("No errors");
                System.out.println();
                return;
            }


            for (MatchedSheet matchedSheet : matchedWorkbook.sheets()) {
                if (matchedSheet.getValidationErrors().isEmpty()) {
                    continue;
                }
                System.out.println(matchedSheet.getSheet().getSheetName());
                for (ValidationError validationError : matchedSheet.getValidationErrors()) {
                    System.out.println(validationError.getMessage() + " @ " + validationError.getRowIdx() + "," +
                            validationError.getCellIdx());
                }
            }

            System.out.println();
        }
        catch (Exception e) {
            e.printStackTrace(System.out);
            System.out.println();
        }
    }

    @Override
    protected void endFile() {
    }

}
