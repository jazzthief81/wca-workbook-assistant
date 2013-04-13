package org.worldcubeassociation.workbook;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.worldcubeassociation.db.Database;
import org.worldcubeassociation.db.Person;
import org.worldcubeassociation.workbook.parse.CellFormatter;
import org.worldcubeassociation.workbook.parse.CellParser;
import org.worldcubeassociation.workbook.parse.ParsedGender;
import org.worldcubeassociation.workbook.parse.RowTokenizer;

import java.text.ParseException;
import java.util.*;

/**
 * @author Lars Vandenbergh
 */
public class WorkbookValidator {

    private static final String[] ORDER = {"1st", "2nd", "3rd", "4th", "5th"};
    private static final Long TEN_MINUTES_IN_CENTISECONDS = 10L * 60L * 100L;

    public static void validate(MatchedWorkbook aMatchedWorkbook, Database aDatabase) {
        for (MatchedSheet matchedSheet : aMatchedWorkbook.sheets()) {
            validateSheet(matchedSheet, aMatchedWorkbook, aDatabase);
        }
    }

    public static void validateSheetsForEvent(MatchedWorkbook aMatchedWorkbook, Event aEvent, Database aDatabase) {
        for (MatchedSheet matchedSheet : aMatchedWorkbook.sheets()) {
            if (matchedSheet.getSheetType() == SheetType.RESULTS && aEvent.equals(matchedSheet.getEvent())) {
                validateSheet(matchedSheet, aMatchedWorkbook, aDatabase);
            }
        }
    }

    public static void validateSheet(MatchedSheet aMatchedSheet, MatchedWorkbook aMatchedWorkbook, Database aDatabase) {
        // Clear validation errors.
        aMatchedSheet.getValidationErrors().clear();
        aMatchedSheet.setValidated(false);

        if (aMatchedSheet.getSheetType() == SheetType.REGISTRATIONS) {
            validateRegistrationsSheet(aMatchedSheet, aDatabase);
        }
        else if (aMatchedSheet.getSheetType() == SheetType.RESULTS) {
            validateResultsSheet(aMatchedSheet, aMatchedWorkbook, aDatabase);
        }
    }

    private static void validateRegistrationsSheet(MatchedSheet aMatchedSheet, Database aDatabase) {
        // Validate name, country.
        Sheet sheet = aMatchedSheet.getSheet();
        for (int rowIdx = aMatchedSheet.getFirstDataRow(); rowIdx <= aMatchedSheet.getLastDataRow(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);

            int headerColIdx = aMatchedSheet.getNameHeaderColumn();
            String name = CellParser.parseMandatoryText(row.getCell(headerColIdx));
            if (name == null) {
                ValidationError validationError = new ValidationError("Missing name", aMatchedSheet, rowIdx, headerColIdx);
                aMatchedSheet.getValidationErrors().add(validationError);
            }

            int countryColIdx = aMatchedSheet.getCountryHeaderColumn();
            String country = CellParser.parseMandatoryText(row.getCell(countryColIdx));
            if (country == null) {
                ValidationError validationError = new ValidationError("Missing country", aMatchedSheet, rowIdx, countryColIdx);
                aMatchedSheet.getValidationErrors().add(validationError);
            }

            int genderColIdx = aMatchedSheet.getGenderHeaderColumn();
            ParsedGender gender = CellParser.parseGender(row.getCell(genderColIdx));
            if (gender == null) {
                ValidationError validationError = new ValidationError("Misformatted gender", aMatchedSheet, rowIdx, genderColIdx);
                aMatchedSheet.getValidationErrors().add(validationError);
            }

            int wcaColIdx = aMatchedSheet.getWcaIdHeaderColumn();
            String wcaId = CellParser.parseOptionalText(row.getCell(wcaColIdx));
            boolean wcaIdEmpty = "".equals(wcaId);
            boolean wcaIdValid = validateWCAId(wcaId);
            if (!wcaIdEmpty && !wcaIdValid) {
                ValidationError validationError = new ValidationError("Misformatted WCA id", aMatchedSheet, rowIdx, 3);
                aMatchedSheet.getValidationErrors().add(validationError);
            }

            // If we have a valid WCA id check that it is in the database and check that the name and country matches
            // what what is in the database.
            if (wcaIdValid && aDatabase != null) {
                Person person = aDatabase.getPersons().findById(wcaId);
                if (person == null) {
                    ValidationError validationError = new ValidationError("Unknown WCA id", aMatchedSheet, rowIdx, 3);
                    aMatchedSheet.getValidationErrors().add(validationError);
                }
                else {
                    if (name != null && !name.equals(person.getName())) {
                        ValidationError validationError = new ValidationError("Name does not match name in WCA database: " + person.getName(), aMatchedSheet, rowIdx, 1);
                        aMatchedSheet.getValidationErrors().add(validationError);
                    }
                    if (country != null && !country.equals(person.getCountry())) {
                        ValidationError validationError = new ValidationError("Country does not match country in WCA database: " + person.getCountry(), aMatchedSheet, rowIdx, 2);
                        aMatchedSheet.getValidationErrors().add(validationError);
                    }
                }
            }
        }

        aMatchedSheet.setValidated(true);
    }

    private static void validateResultsSheet(MatchedSheet aMatchedSheet, MatchedWorkbook aMatchedWorkbook, Database aDatabase) {
        // Validate round, event, format and result format.
        boolean validResultFormat = true;
        if (aMatchedSheet.getEvent() == null) {
            ValidationError validationError = new ValidationError("Missing event", aMatchedSheet, -1, ValidationError.EVENT_CELL_IDX);
            aMatchedSheet.getValidationErrors().add(validationError);
        }
        if (aMatchedSheet.getRound() == null) {
            ValidationError validationError = new ValidationError("Missing round", aMatchedSheet, -1, ValidationError.ROUND_CELL_IDX);
            aMatchedSheet.getValidationErrors().add(validationError);
        }
        if (aMatchedSheet.getFormat() == null) {
            ValidationError validationError = new ValidationError("Missing format", aMatchedSheet, -1, ValidationError.FORMAT_CELL_IDX);
            aMatchedSheet.getValidationErrors().add(validationError);
        }
        if (aMatchedSheet.getResultFormat() == null) {
            ValidationError validationError = new ValidationError("Missing result format", aMatchedSheet, -1, ValidationError.RESULT_FORMAT_CELL_IDX);
            aMatchedSheet.getValidationErrors().add(validationError);
        }
        else if (aMatchedSheet.getEvent() != null) {
            if (aMatchedSheet.getEvent() == Event._333mbf || aMatchedSheet.getEvent() == Event._333fm) {
                validResultFormat = aMatchedSheet.getResultFormat() == ResultFormat.NUMBER;
            }
            else {
                validResultFormat = aMatchedSheet.getResultFormat() != ResultFormat.NUMBER;
            }
            if (!validResultFormat) {
                ValidationError validationError = new ValidationError("Illegal result format for event", aMatchedSheet, -1, ValidationError.RESULT_FORMAT_CELL_IDX);
                aMatchedSheet.getValidationErrors().add(validationError);
            }
        }

        // Check for duplicate event/round combination.
        if (aMatchedSheet.getEvent() != null && aMatchedSheet.getRound() != null) {
            List<MatchedSheet> sheets = aMatchedWorkbook.sheets();
            List<MatchedSheet> duplicateSheets = new ArrayList<MatchedSheet>();
            for (MatchedSheet sheet : sheets) {
                if (aMatchedSheet.getEvent().equals(sheet.getEvent()) &&
                        aMatchedSheet.getRound().isSameRoundAs(sheet.getRound())) {
                    duplicateSheets.add(sheet);
                }
            }

            if (duplicateSheets.size() > 1) {
                StringBuffer sheetNames = new StringBuffer();
                for (int i = 0, duplicateSheetsSize = duplicateSheets.size(); i < duplicateSheetsSize; i++) {
                    if (i == duplicateSheetsSize - 1) {
                        sheetNames.append(" and ");
                    }
                    else if (i > 0) {
                        sheetNames.append(", ");
                    }
                    MatchedSheet duplicateSheet = duplicateSheets.get(i);
                    sheetNames.append(duplicateSheet.getSheet().getSheetName());
                }
                ValidationError validationError = new ValidationError("Duplicate round for event in sheets " + sheetNames, aMatchedSheet, -1, ValidationError.ROUND_CELL_IDX);
                aMatchedSheet.getValidationErrors().add(validationError);
            }
        }

        // Validate position, name, country and WCA ID.
        Sheet sheet = aMatchedSheet.getSheet();
        FormulaEvaluator formulaEvaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        for (int rowIdx = aMatchedSheet.getFirstDataRow(); rowIdx <= aMatchedSheet.getLastDataRow(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);

            Long position = CellParser.parsePosition(row.getCell(0));
            if (position == null) {
                ValidationError validationError = new ValidationError("Missing position", aMatchedSheet, rowIdx, 0);
                aMatchedSheet.getValidationErrors().add(validationError);
            }

            String name = CellParser.parseMandatoryText(row.getCell(1));
            if (name == null) {
                ValidationError validationError = new ValidationError("Missing name", aMatchedSheet, rowIdx, 1);
                aMatchedSheet.getValidationErrors().add(validationError);
            }

            String country = CellParser.parseMandatoryText(row.getCell(2));
            if (country == null) {
                ValidationError validationError = new ValidationError("Missing country", aMatchedSheet, rowIdx, 2);
                aMatchedSheet.getValidationErrors().add(validationError);
            }

            String wcaId = CellParser.parseOptionalText(row.getCell(3));
            boolean wcaIdEmpty = "".equals(wcaId);
            boolean wcaIdValid = validateWCAId(wcaId);
            if (!wcaIdEmpty && !wcaIdValid) {
                ValidationError validationError = new ValidationError("Misformatted WCA id", aMatchedSheet, rowIdx, 3);
                aMatchedSheet.getValidationErrors().add(validationError);
            }

            // If we have a valid WCA id check that it is in the database and check that the name and country matches
            // with what is in the database.
            if (wcaIdValid && aDatabase != null) {
                Person person = aDatabase.getPersons().findById(wcaId);
                if (person == null) {
                    ValidationError validationError = new ValidationError("Unknown WCA id", aMatchedSheet, rowIdx, 3);
                    aMatchedSheet.getValidationErrors().add(validationError);
                }
                else {
                    if (name != null && !name.equals(person.getName())) {
                        ValidationError validationError = new ValidationError("Name does not match name in WCA database: " + person.getName(), aMatchedSheet, rowIdx, 1);
                        aMatchedSheet.getValidationErrors().add(validationError);
                    }
                    if (country != null && !country.equals(person.getCountry())) {
                        ValidationError validationError = new ValidationError("Country does not match country in WCA database: " + person.getCountry(), aMatchedSheet, rowIdx, 2);
                        aMatchedSheet.getValidationErrors().add(validationError);
                    }
                }
            }
        }

        // Validate results.
        if (aMatchedSheet.getEvent() != null &&
                aMatchedSheet.getRound() != null &&
                aMatchedSheet.getFormat() != null &&
                aMatchedSheet.getResultFormat() != null &&
                validResultFormat) {
            validateResults(aMatchedSheet, formulaEvaluator);
        }

        aMatchedSheet.setValidated(true);
    }

    private static void validateResults(MatchedSheet aMatchedSheet, FormulaEvaluator aFormulaEvaluator) {
        List<ValidationError> validationErrors = aMatchedSheet.getValidationErrors();
        Sheet sheet = aMatchedSheet.getSheet();
        Event event = aMatchedSheet.getEvent();
        Round round = aMatchedSheet.getRound();
        Format format = aMatchedSheet.getFormat();
        ResultFormat resultFormat = aMatchedSheet.getResultFormat();
        ColumnOrder columnOrder = aMatchedSheet.getColumnOrder();

        boolean allRowsValid = true;
        int nbDataRows = aMatchedSheet.getLastDataRow() - aMatchedSheet.getFirstDataRow() + 1;
        Long[] bestResults = new Long[nbDataRows];
        Long[] averageResults = new Long[nbDataRows];

        for (int rowIdx = 0; rowIdx < nbDataRows; rowIdx++) {
            Row row = sheet.getRow(rowIdx + aMatchedSheet.getFirstDataRow());
            int sheetRow = rowIdx + aMatchedSheet.getFirstDataRow();

            // Validate individual results.
            boolean allResultsInRowValid = true;
            boolean allResultsInRowPresent = true;
            Long[] results = new Long[format.getResultCount()];
            for (int resultIdx = 1; resultIdx <= format.getResultCount(); resultIdx++) {
                int resultCellCol = RowTokenizer.getResultCell(resultIdx, format, event, columnOrder);
                Cell resultCell = row.getCell(resultCellCol);

                try {
                    Long result;
                    if (round.isCombined() && resultIdx > 1) {
                        result = CellParser.parseOptionalSingleTime(resultCell, resultFormat, aFormulaEvaluator);
                    }
                    else {
                        result = CellParser.parseMandatorySingleTime(resultCell, resultFormat, aFormulaEvaluator);
                    }
                    results[resultIdx - 1] = result;

                    if (result == 0) {
                        allResultsInRowPresent = false;
                    }
                    else {
                        if ((resultFormat == ResultFormat.SECONDS || resultFormat == ResultFormat.MINUTES) &&
                                !roundToNearestSecond(result).equals(result)) {
                            validationErrors.add(new ValidationError(ORDER[resultIdx - 1] + " result is over 10 minutes and should be rounded to the nearest second",
                                    aMatchedSheet, sheetRow, resultCellCol));
                            allResultsInRowValid = false;
                            allRowsValid = false;
                        }
                    }
                }
                catch (ParseException e) {
                    validationErrors.add(new ValidationError(ORDER[resultIdx - 1] + " result: " + e.getMessage(),
                            aMatchedSheet, sheetRow, resultCellCol));
                    allResultsInRowValid = false;
                    allRowsValid = false;
                }
            }

            // Validate best result.
            if (format.getResultCount() > 1) {
                int bestCellCol = RowTokenizer.getBestCell(format, event, columnOrder);
                Cell bestResultCell = row.getCell(bestCellCol);

                try {
                    Long bestResult = CellParser.parseMandatorySingleTime(bestResultCell, resultFormat, aFormulaEvaluator);
                    bestResults[rowIdx] = bestResult;
                    if (allResultsInRowValid) {
                        Long expectedBestResult = calculateBestResult(results);
                        if (!expectedBestResult.equals(bestResult)) {
                            String formattedExpectedBest = CellFormatter.formatTime(expectedBestResult, resultFormat);
                            validationErrors.add(new ValidationError("Best result does not match calculated best result: " + formattedExpectedBest,
                                    aMatchedSheet, sheetRow, bestCellCol));
                            allRowsValid = false;
                        }
                    }
                }
                catch (ParseException e) {
                    validationErrors.add(new ValidationError("Best result: " + e.getMessage(), aMatchedSheet, sheetRow, bestCellCol));
                    allRowsValid = false;
                }
            }
            else {
                bestResults[rowIdx] = results[0];
            }

            // Validate single record.
            int singleRecordCellCol = RowTokenizer.getSingleRecordCell(format, event, columnOrder);
            Cell singleRecordCell = row.getCell(singleRecordCellCol);
            try {
                CellParser.parseRecord(singleRecordCell);
            }
            catch (ParseException e) {
                validationErrors.add(new ValidationError("Misformatted single record", aMatchedSheet, sheetRow, singleRecordCellCol));
            }

            // Validate average result.
            if (format == Format.MEAN_OF_3 || format == Format.AVERAGE_OF_5) {
                int averageCellCol = RowTokenizer.getAverageCell(format, event);
                Cell averageResultCell = row.getCell(averageCellCol);

                try {
                    Long averageResult;
                    if (round.isCombined()) {
                        averageResult = CellParser.parseOptionalAverageTime(averageResultCell, resultFormat, aFormulaEvaluator);
                    }
                    else {
                        averageResult = CellParser.parseMandatoryAverageTime(averageResultCell, resultFormat, aFormulaEvaluator);
                    }
                    averageResults[rowIdx] = averageResult;

                    if (allResultsInRowValid) {
                        if (allResultsInRowPresent) {
                            Long expectedAverageResult = calculateAverageResult(results, format);
                            if (!expectedAverageResult.equals(averageResult)) {
                                String formattedExpectedAverage = CellFormatter.formatTime(expectedAverageResult, resultFormat);
                                validationErrors.add(new ValidationError("Average result does not match calculated average result: " + formattedExpectedAverage,
                                        aMatchedSheet, sheetRow, averageCellCol));
                                allRowsValid = false;
                            }
                        }
                        else {
                            if (!averageResult.equals(0L)) {
                                validationErrors.add(new ValidationError("Average result should be empty for incomplete average in combined round",
                                        aMatchedSheet, sheetRow, averageCellCol));
                                allRowsValid = false;
                            }
                        }
                    }
                }
                catch (ParseException e) {
                    validationErrors.add(new ValidationError("Average result: " + e.getMessage(), aMatchedSheet, sheetRow, averageCellCol));
                    allRowsValid = false;
                }

                // Validate average record.
                int averageRecordCellCol = RowTokenizer.getAverageRecordCell(format, event);
                Cell averageRecordCell = row.getCell(averageRecordCellCol);
                try {
                    CellParser.parseRecord(averageRecordCell);
                }
                catch (ParseException e) {
                    validationErrors.add(new ValidationError("Misformatted average record", aMatchedSheet, sheetRow, averageRecordCellCol));
                }
            }
        }

        // Check sorting.
        if (allRowsValid) {
            List<ResultRow> resultRows = new ArrayList<ResultRow>();
            for (int rowIdx = 0; rowIdx < nbDataRows; rowIdx++) {
                int sheetRow = rowIdx + aMatchedSheet.getFirstDataRow();
                ResultRow resultRow = new ResultRow(sheetRow, bestResults[rowIdx], averageResults[rowIdx]);
                resultRows.add(resultRow);
            }

            Comparator<ResultRow> resultRowComparator;
            if (format == Format.MEAN_OF_3 || format == Format.AVERAGE_OF_5) {
                resultRowComparator = new AverageResultRowComparator();
            }
            else {
                resultRowComparator = new BestResultRowComparator();
            }
            Collections.sort(resultRows, resultRowComparator);

            boolean isSorted = true;
            for (int rowIdx = 0; rowIdx < nbDataRows; rowIdx++) {
                int sheetRow = rowIdx + aMatchedSheet.getFirstDataRow();
                ResultRow sortedResultRow = resultRows.get(rowIdx);
                int rowDifference = sortedResultRow.getRowIdx() - sheetRow;
                if (rowDifference != 0) {
                    String direction = rowDifference > 0 ? "higher" : "lower";
                    int absRowDiff = Math.abs(rowDifference);
                    String number = absRowDiff > 1 ? "rows" : "row";
                    validationErrors.add(new ValidationError("Bad sorting: row should be " + absRowDiff + " " + number + " " + direction,
                            aMatchedSheet, sortedResultRow.getRowIdx(), -1));
                    isSorted = false;
                }
            }

            // Check positions.
            if (isSorted) {
                Long expectedPosition = 1L;
                for (int rowIdx = 0; rowIdx < nbDataRows; rowIdx++) {
                    if (rowIdx > 0) {
                        ResultRow lastRow = resultRows.get(rowIdx - 1);
                        ResultRow currentRow = resultRows.get(rowIdx);
                        if (resultRowComparator.compare(lastRow, currentRow) != 0) {
                            expectedPosition = rowIdx + 1L;
                        }
                    }
                    else {
                        expectedPosition = 1L;
                    }
                    int sheetRow = rowIdx + aMatchedSheet.getFirstDataRow();
                    Row row = sheet.getRow(sheetRow);
                    Long position = CellParser.parsePosition(row.getCell(0));
                    if (position != null && !expectedPosition.equals(position)) {
                        validationErrors.add(new ValidationError("Position does not match expected position: " + expectedPosition,
                                aMatchedSheet, sheetRow, 0));
                    }
                }
            }
        }


        // Sort validation errors by row and cell.
        Collections.sort(validationErrors, new ValidationErrorComparator());
    }

    private static boolean validateWCAId(String aWcaId) {
        return aWcaId.matches("[0-9]{4}[A-Z]{4}[0-9]{2}");
    }

    private static Long calculateBestResult(Long[] aResults) {
        Long best = null;
        for (Long result : aResults) {
            if (result > 0) {
                if (best == null || result < best) {
                    best = result;
                }
            }
        }

        if (best == null) {
            return -1L;
        }
        else {
            return best;
        }
    }

    private static Long calculateAverageResult(Long[] aResults, Format aFormat) {
        Long[] resultsCopy = Arrays.copyOf(aResults, aResults.length);
        Arrays.sort(resultsCopy, new ResultComparator());
        if (aFormat == Format.AVERAGE_OF_5) {
            resultsCopy = Arrays.copyOfRange(resultsCopy, 1, 4);
        }

        double sum = 0;
        for (Long result : resultsCopy) {
            if (result > 0) {
                sum += result;
            }
            else {
                return -1L;
            }
        }

        long average = Math.round(sum / resultsCopy.length);
        return roundToNearestSecond(average);
    }

    private static Long roundToNearestSecond(Long aResult) {
        if (aResult > TEN_MINUTES_IN_CENTISECONDS) {
            return Math.round(aResult / 100.0) * 100;
        }
        else {
            return aResult;
        }
    }

    private static class ResultComparator implements Comparator<Long> {
        @Override
        public int compare(Long aFirst, Long aSecond) {
            if (aFirst == -2 || aFirst == -1) {
                if (aSecond == -2 || aSecond == -1) {
                    // A DNS/DNF is equal to a DNS/DNF.
                    return 0;
                }
                else {
                    // A DNS/DNF is larger than a time.
                    return 1;
                }
            }
            else {
                if (aSecond == -2 || aSecond == -1) {
                    // A time is smaller than a DNS/DNF.
                    return -1;
                }
                else {
                    // A lower time is smaller than a larger time.
                    return (int) (aFirst - aSecond);
                }
            }
        }
    }

    private static class AverageResultRowComparator implements Comparator<ResultRow> {

        private ResultComparator fResultComparator = new ResultComparator();

        @Override
        public int compare(ResultRow aFirstResultRow, ResultRow aSecondResultRow) {
            int bestCompare = fResultComparator.compare(aFirstResultRow.getBestResult(),
                    aSecondResultRow.getBestResult());
            if (aFirstResultRow.getAverageResult() != 0L && aSecondResultRow.getAverageResult() != 0L) {
                int averageCompare = fResultComparator.compare(aFirstResultRow.getAverageResult(),
                        aSecondResultRow.getAverageResult());
                if (averageCompare != 0) {
                    return averageCompare;
                }
                else {
                    return bestCompare;
                }
            }
            else if (aFirstResultRow.getAverageResult() != 0L && aSecondResultRow.getAverageResult() == 0L) {
                return -1;
            }
            else {
                return bestCompare;
            }
        }

    }

    private static class BestResultRowComparator implements Comparator<ResultRow> {

        private ResultComparator fResultComparator = new ResultComparator();

        @Override
        public int compare(ResultRow aFirstResultRow, ResultRow aSecondResultRow) {
            return fResultComparator.compare(aFirstResultRow.getBestResult(), aSecondResultRow.getBestResult());
        }

    }

    private static class ValidationErrorComparator implements Comparator<ValidationError> {

        @Override
        public int compare(ValidationError aFirstValidationError, ValidationError aSecondValidationError) {
            if (aFirstValidationError.getRowIdx() == -1) {
                return -1;
            }
            else {
                if (aSecondValidationError.getRowIdx() == -1) {
                    return 1;
                }
                else {
                    if (aFirstValidationError.getCellIdx() == -1 && aSecondValidationError.getCellIdx() != -1) {
                        return -1;
                    }
                    else if (aFirstValidationError.getCellIdx() != -1 && aSecondValidationError.getCellIdx() == -1) {
                        return 1;
                    }
                    else if (aFirstValidationError.getCellIdx() == -1 && aSecondValidationError.getCellIdx() == -1) {
                        return aFirstValidationError.getRowIdx() - aSecondValidationError.getRowIdx();
                    }
                    else {
                        int rowDifference = aFirstValidationError.getRowIdx() - aSecondValidationError.getRowIdx();
                        if (rowDifference == 0) {
                            return aFirstValidationError.getCellIdx() - aSecondValidationError.getCellIdx();
                        }
                        else {
                            return rowDifference;
                        }
                    }
                }
            }
        }

    }

}
