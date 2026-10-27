package com.iskolar.controller;

import com.iskolar.dao.DaoFactory;
import com.iskolar.dto.ScholarshipDraft;
import com.iskolar.dto.TagRequirement;
import com.iskolar.model.DegreeProgram;
import com.iskolar.model.DocumentType;
import com.iskolar.model.ProviderType;
import com.iskolar.model.Scholarship;
import com.iskolar.model.ScholarshipCriteria;
import com.iskolar.model.SchoolType;
import com.iskolar.model.Tag;
import com.iskolar.util.SessionManager;
import com.iskolar.util.ViewNavigator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin create/edit form for a scholarship and all of its structured
 * eligibility rules. Saves atomically via {@code ScholarshipDao.create/update}.
 */
public class AdminScholarshipFormController {

    /** Mutable checkbox state wrapper for ListView rows. */
    private static final class Selectable<T> {
        final T value;
        final BooleanProperty selected = new SimpleBooleanProperty(false);

        Selectable(T value) {
            this.value = value;
        }
    }

    @FXML private Label formTitle;
    @FXML private TextField titleField;
    @FXML private TextField providerField;
    @FXML private ComboBox<ProviderType> providerTypeCombo;
    @FXML private TextArea descriptionArea;
    @FXML private TextField tuitionField;
    @FXML private TextField stipendField;
    @FXML private TextField returnServiceField;
    @FXML private TextField urlField;
    @FXML private DatePicker openDatePicker;
    @FXML private DatePicker deadlinePicker;
    @FXML private CheckBox activeCheck;
    @FXML private TextField minGwaField;
    @FXML private TextField maxIncomeField;
    @FXML private ComboBox<SchoolType> schoolTypeCombo;
    @FXML private CheckBox allowFailingCheck;
    @FXML private TextField requiredProvinceField;
    @FXML private TextField requiredCityField;
    @FXML private ListView<Selectable<DegreeProgram>> programsList;
    @FXML private CheckBox year1;
    @FXML private CheckBox year2;
    @FXML private CheckBox year3;
    @FXML private CheckBox year4;
    @FXML private CheckBox year5;
    @FXML private ListView<Selectable<Tag>> mandatoryTagsList;
    @FXML private ListView<Selectable<Tag>> preferredTagsList;
    @FXML private ListView<Selectable<DocumentType>> documentsList;
    @FXML private Label errorLabel;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private Long editingId;
    private long createdBy = 1L;
    private CheckBox[] yearBoxes;

    @FXML
    private void initialize() {
        yearBoxes = new CheckBox[]{year1, year2, year3, year4, year5};

        providerTypeCombo.getItems().addAll(ProviderType.values());
        providerTypeCombo.setConverter(displayConverter(ProviderType::displayName));
        schoolTypeCombo.getItems().addAll(SchoolType.values());
        schoolTypeCombo.setConverter(displayConverter(SchoolType::displayName));

        programsList.setCellFactory(list -> {
            CheckBoxListCell<Selectable<DegreeProgram>> cell =
                    new CheckBoxListCell<>(item -> item.selected);
            cell.setConverter(displayConverter(item -> item.value.displayLabel()));
            return cell;
        });
        mandatoryTagsList.setCellFactory(list -> {
            CheckBoxListCell<Selectable<Tag>> cell =
                    new CheckBoxListCell<>(item -> item.selected);
            cell.setConverter(displayConverter(item -> item.value.tagName()));
            return cell;
        });
        preferredTagsList.setCellFactory(list -> {
            CheckBoxListCell<Selectable<Tag>> cell =
                    new CheckBoxListCell<>(item -> item.selected);
            cell.setConverter(displayConverter(item -> item.value.tagName()));
            return cell;
        });
        documentsList.setCellFactory(list -> {
            CheckBoxListCell<Selectable<DocumentType>> cell =
                    new CheckBoxListCell<>(item -> item.selected);
            cell.setConverter(displayConverter(item -> item.value.docName()));
            return cell;
        });

        programsList.getItems().setAll(
                DaoFactory.degreeProgramDao().findAll().stream().map(Selectable::new).toList());
        mandatoryTagsList.getItems().setAll(
                DaoFactory.tagDao().findAll().stream().map(Selectable::new).toList());
        preferredTagsList.getItems().setAll(
                DaoFactory.tagDao().findAll().stream().map(Selectable::new).toList());
        documentsList.getItems().setAll(
                DaoFactory.documentDao().findAll().stream().map(Selectable::new).toList());

        // A tag cannot be both mandatory and preferred — keep the lists exclusive.
        mandatoryTagsList.getItems().forEach(m -> m.selected.addListener((obs, old, val) -> {
            if (Boolean.TRUE.equals(val)) {
                preferredTagsList.getItems().stream()
                        .filter(p -> p.value.tagId() == m.value.tagId())
                        .findFirst().ifPresent(p -> p.selected.set(false));
            }
        }));
        preferredTagsList.getItems().forEach(p -> p.selected.addListener((obs, old, val) -> {
            if (Boolean.TRUE.equals(val)) {
                mandatoryTagsList.getItems().stream()
                        .filter(m -> m.value.tagId() == p.value.tagId())
                        .findFirst().ifPresent(m -> m.selected.set(false));
            }
        }));

        saveButton.setOnAction(e -> save());
        cancelButton.setOnAction(e -> ViewNavigator.show(ViewNavigator.ADMIN_DASHBOARD_VIEW));
    }

    /** Callback from {@code ViewNavigator} when creating. */
    public void initNew() {
        editingId = null;
        formTitle.setText("New Scholarship");
        activeCheck.setSelected(true);
        createdBy = SessionManager.current().map(s -> s.userId()).orElse(1L);
    }

    /** Callback from {@code ViewNavigator} when editing. */
    public void initEdit(long scholarshipId) {
        editingId = scholarshipId;
        formTitle.setText("Edit Scholarship");
        var scholarship = DaoFactory.scholarshipDao().findById(scholarshipId).orElseThrow();
        createdBy = scholarship.createdBy();

        titleField.setText(scholarship.title());
        providerField.setText(scholarship.providerName());
        providerTypeCombo.setValue(scholarship.providerType());
        descriptionArea.setText(scholarship.description());
        tuitionField.setText(scholarship.tuitionBenefit());
        stipendField.setText(scholarship.monthlyStipend() == null ? "" : scholarship.monthlyStipend().toPlainString());
        returnServiceField.setText(String.valueOf(scholarship.returnServiceYears()));
        urlField.setText(scholarship.applicationUrl());
        openDatePicker.setValue(scholarship.openDate());
        deadlinePicker.setValue(scholarship.deadlineDate());
        activeCheck.setSelected(scholarship.isActive());

        DaoFactory.scholarshipDao().findCriteria(scholarshipId).ifPresent(c -> {
            minGwaField.setText(c.minGwaPercentage() == null ? "" : c.minGwaPercentage().toString());
            maxIncomeField.setText(c.maxAnnualIncome() == null ? "" : c.maxAnnualIncome().toPlainString());
            schoolTypeCombo.setValue(c.allowedSchoolType());
            allowFailingCheck.setSelected(c.allowFailingGrade());
            requiredProvinceField.setText(c.requiredProvince());
            requiredCityField.setText(c.requiredCity());
        });

        List<Integer> programs = DaoFactory.scholarshipDao().findAllowedProgramIds(scholarshipId);
        programsList.getItems().forEach(item -> item.selected.set(programs.contains(item.value.programId())));

        List<Integer> years = DaoFactory.scholarshipDao().findAllowedYearLevels(scholarshipId);
        for (int i = 0; i < yearBoxes.length; i++) {
            yearBoxes[i].setSelected(years.contains(i + 1));
        }

        List<TagRequirement> tags = DaoFactory.scholarshipDao().findTagRequirements(scholarshipId);
        for (TagRequirement req : tags) {
            var target = req.mandatory() ? mandatoryTagsList : preferredTagsList;
            target.getItems().stream()
                    .filter(item -> item.value.tagId() == req.tag().tagId())
                    .findFirst().ifPresent(item -> item.selected.set(true));
        }

        List<Integer> docs = DaoFactory.scholarshipDao().findRequiredDocumentIds(scholarshipId);
        documentsList.getItems().forEach(item -> item.selected.set(docs.contains(item.value.documentTypeId())));
    }

    private void save() {
        clearError();
        String title = trim(titleField);
        if (title == null) {
            fail("Enter a scholarship title.");
            return;
        }
        String provider = trim(providerField);
        if (provider == null) {
            fail("Enter the provider name.");
            return;
        }
        if (providerTypeCombo.getValue() == null) {
            fail("Select the provider type.");
            return;
        }

        // stipend (empty = 0)
        BigDecimal stipend = BigDecimal.ZERO;
        String stipendText = text(stipendField);
        if (!stipendText.isEmpty()) {
            try {
                stipend = new BigDecimal(stipendText);
            } catch (NumberFormatException e) {
                fail("Enter a valid monthly stipend (numbers only).");
                return;
            }
        }

        // return service years (empty = 0)
        int returnYears = 0;
        String returnText = text(returnServiceField);
        if (!returnText.isEmpty()) {
            try {
                returnYears = Integer.parseInt(returnText);
            } catch (NumberFormatException e) {
                fail("Enter a valid return-of-service year count.");
                return;
            }
            if (returnYears < 0) {
                fail("Return-of-service years cannot be negative.");
                return;
            }
        }

        LocalDate open = openDatePicker.getValue();
        LocalDate deadline = deadlinePicker.getValue();
        if (deadline == null) {
            fail("Set a deadline date.");
            return;
        }
        if (open != null && open.isAfter(deadline)) {
            fail("The open date must be before the deadline.");
            return;
        }

        // minimum GWA (empty = unrestricted)
        Double minGwa = null;
        String gwaText = text(minGwaField);
        if (!gwaText.isEmpty()) {
            try {
                minGwa = Double.parseDouble(gwaText);
            } catch (NumberFormatException e) {
                fail("Enter a valid minimum GWA (0-100), or leave it empty.");
                return;
            }
            if (minGwa < 0 || minGwa > 100) {
                fail("Minimum GWA must be between 0 and 100.");
                return;
            }
        }

        // income cap (empty = merit-only)
        BigDecimal maxIncome = null;
        String incomeText = text(maxIncomeField);
        if (!incomeText.isEmpty()) {
            try {
                maxIncome = new BigDecimal(incomeText);
            } catch (NumberFormatException e) {
                fail("Enter a valid income cap, or leave it empty for merit-only.");
                return;
            }
            if (maxIncome.signum() < 0) {
                fail("Income cap cannot be negative.");
                return;
            }
        }

        Scholarship scholarship = new Scholarship(
                editingId == null ? 0 : editingId,
                createdBy,
                title,
                provider,
                providerTypeCombo.getValue(),
                trim(descriptionArea),
                trim(tuitionField),
                stipend,
                returnYears,
                trim(urlField),
                open,
                deadline,
                activeCheck.isSelected());
        ScholarshipCriteria criteria = new ScholarshipCriteria(
                0, 0,
                minGwa,
                allowFailingCheck.isSelected(),
                maxIncome,
                schoolTypeCombo.getValue(),
                trim(requiredProvinceField),
                trim(requiredCityField));

        List<Integer> programs = programsList.getItems().stream()
                .filter(i -> i.selected.get())
                .map(i -> i.value.programId())
                .toList();
        List<Integer> years = new ArrayList<>();
        for (int i = 0; i < yearBoxes.length; i++) {
            if (yearBoxes[i].isSelected()) {
                years.add(i + 1);
            }
        }
        List<TagRequirement> tags = new ArrayList<>();
        mandatoryTagsList.getItems().stream().filter(i -> i.selected.get())
                .forEach(i -> tags.add(new TagRequirement(i.value, true)));
        preferredTagsList.getItems().stream().filter(i -> i.selected.get())
                .forEach(i -> tags.add(new TagRequirement(i.value, false)));
        List<Integer> documents = documentsList.getItems().stream()
                .filter(i -> i.selected.get())
                .map(i -> i.value.documentTypeId())
                .toList();

        ScholarshipDraft draft = new ScholarshipDraft(scholarship, criteria, programs, years, tags, documents);
        if (editingId == null) {
            DaoFactory.scholarshipDao().create(draft);
        } else {
            DaoFactory.scholarshipDao().update(editingId, draft);
        }
        ViewNavigator.show(ViewNavigator.ADMIN_DASHBOARD_VIEW);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static <T> StringConverter<T> displayConverter(java.util.function.Function<T, String> label) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : label.apply(value);
            }

            @Override
            public T fromString(String s) {
                return null;
            }
        };
    }

    private void fail(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private static String text(TextField field) {
        String value = field.getText();
        return value == null ? "" : value.trim();
    }

    private static String trim(TextField field) {
        String value = field.getText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trim(TextArea area) {
        String value = area.getText();
        return value == null || value.isBlank() ? null : value.trim();
    }
}
