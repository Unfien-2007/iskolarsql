package com.iskolar.controller;

import com.iskolar.dao.DaoFactory;
import com.iskolar.model.DegreeProgram;
import com.iskolar.model.DocumentType;
import com.iskolar.model.SchoolType;
import com.iskolar.model.Sex;
import com.iskolar.model.StudentProfile;
import com.iskolar.model.Tag;
import com.iskolar.service.ServiceFactory;
import com.iskolar.util.GwaConverter;
import com.iskolar.util.SessionManager;
import com.iskolar.util.ViewNavigator;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The progressive 4-step profile wizard. Steps save as you advance:
 * profile row after Steps 1-3, tags after Step 3, document vault on Finish.
 */
public class WizardController {

    private static final String GWA_FIVE_POINT = "5-point scale (1.00–5.00)";
    private static final String GWA_PERCENT = "Percentage (0–100)";

    // shell
    @FXML private VBox gatePane;
    @FXML private Label gateLabel;
    @FXML private Button gateButton;
    @FXML private StackPane stepStack;
    @FXML private Label chip1;
    @FXML private Label chip2;
    @FXML private Label chip3;
    @FXML private Label chip4;
    @FXML private Label errorLabel;
    @FXML private Button backButton;
    @FXML private Button nextButton;

    // step 1
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private ComboBox<Sex> sexCombo;
    @FXML private DatePicker birthdatePicker;
    @FXML private TextField schoolNameField;
    @FXML private ComboBox<SchoolType> schoolTypeCombo;
    @FXML private ComboBox<Integer> yearCombo;
    @FXML private ComboBox<DegreeProgram> programCombo;
    @FXML private TextField gwaField;
    @FXML private ComboBox<String> gwaModeCombo;
    @FXML private CheckBox failingCheck;

    // step 2
    @FXML private TextField incomeField;
    @FXML private TextField householdField;

    // step 3
    @FXML private TextField provinceField;
    @FXML private TextField cityField;
    @FXML private VBox tagsBox;

    // step 4
    @FXML private VBox docsBox;

    private Label[] chips;
    private int step;

    private long userId;
    private int studentId;
    private Double gwaPercent;
    private BigDecimal incomeValue;
    private Integer householdValue;
    private final List<CheckBox> tagChecks = new ArrayList<>();
    private final List<CheckBox> docChecks = new ArrayList<>();

    @FXML
    private void initialize() {
        chips = new Label[]{chip1, chip2, chip3, chip4};

        sexCombo.getItems().addAll(Sex.values());
        sexCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Sex sex) { return sex == null ? "Select" : sex.displayName(); }
            @Override public Sex fromString(String s) { return null; }
        });
        schoolTypeCombo.getItems().addAll(SchoolType.values());
        schoolTypeCombo.setConverter(new StringConverter<>() {
            @Override public String toString(SchoolType t) { return t == null ? "Public / Private" : t.displayName(); }
            @Override public SchoolType fromString(String s) { return null; }
        });
        yearCombo.getItems().addAll(1, 2, 3, 4, 5);
        programCombo.getItems().addAll(DaoFactory.degreeProgramDao().findAll());
        programCombo.setConverter(new StringConverter<>() {
            @Override public String toString(DegreeProgram p) { return p == null ? "Select your program" : p.displayLabel(); }
            @Override public DegreeProgram fromString(String s) { return null; }
        });
        gwaModeCombo.getItems().addAll(GWA_PERCENT, GWA_FIVE_POINT);
        gwaModeCombo.setValue(GWA_PERCENT);

        buildTagChecks();
        buildDocChecks();

        backButton.setOnAction(e -> { clearError(); showStep(step - 1); });
        nextButton.setOnAction(e -> next());

        var session = SessionManager.current();
        if (session.isEmpty()) {
            gateButton.setOnAction(e -> ViewNavigator.show(ViewNavigator.LOGIN_VIEW));
            hideWizard();
            return;
        }
        userId = session.get().userId();
        gatePane.setVisible(false);
        gatePane.setManaged(false);

        ServiceFactory.profileService().findByUserId(userId).ifPresent(this::populateFrom);
        showStep(0);
    }

    private void hideWizard() {
        stepStack.setVisible(false);
        stepStack.setManaged(false);
        gateButton.setVisible(true);
    }

    private void buildTagChecks() {
        for (Tag tag : DaoFactory.tagDao().findAll()) {
            CheckBox box = new CheckBox(tag.tagName());
            box.setUserData(tag.tagId());
            box.getStyleClass().add("tag-check");
            tagChecks.add(box);
            tagsBox.getChildren().add(box);
        }
    }

    private void buildDocChecks() {
        for (DocumentType doc : DaoFactory.documentDao().findAll()) {
            CheckBox box = new CheckBox(doc.docName());
            box.setUserData(doc.documentTypeId());
            box.getStyleClass().add("doc-check");
            docChecks.add(box);
            docsBox.getChildren().add(box);
        }
    }

    private void populateFrom(StudentProfile p) {
        studentId = p.studentId();
        firstNameField.setText(p.firstName());
        lastNameField.setText(p.lastName());
        sexCombo.setValue(p.sex());
        birthdatePicker.setValue(p.birthdate());
        schoolNameField.setText(p.schoolName());
        schoolTypeCombo.setValue(p.schoolType());
        yearCombo.setValue(p.yearLevel());
        programCombo.setValue(p.programId() == null ? null
                : programCombo.getItems().stream()
                .filter(d -> d.programId() == p.programId()).findFirst().orElse(null));
        gwaPercent = p.gwaPercentage();
        gwaModeCombo.setValue(GWA_PERCENT);
        gwaField.setText(gwaPercent > 0 ? GwaConverter.formatPercent(gwaPercent).replace("%", "") : "");
        failingCheck.setSelected(p.hasFailingGrade());
        incomeValue = p.annualFamilyIncome();
        incomeField.setText(incomeValue == null ? "" : incomeValue.toPlainString());
        householdValue = p.householdSize();
        householdField.setText(householdValue == null ? "" : String.valueOf(householdValue));
        provinceField.setText(p.province());
        cityField.setText(p.cityMunicipality());

        Set<Integer> tagIds = DaoFactory.tagDao().findForStudent(studentId).stream()
                .map(Tag::tagId).collect(Collectors.toSet());
        tagChecks.forEach(box -> box.setSelected(tagIds.contains(box.getUserData())));

        Map<Integer, Boolean> ready = new HashMap<>();
        DaoFactory.documentDao().findForStudent(studentId)
                .forEach(d -> ready.put(d.documentTypeId(), d.isReady()));
        docChecks.forEach(box -> box.setSelected(ready.getOrDefault(box.getUserData(), false)));
    }

    // ------------------------------------------------------------------
    // Step navigation
    // ------------------------------------------------------------------

    private void showStep(int index) {
        step = index;
        for (int i = 0; i < stepStack.getChildren().size(); i++) {
            Node pane = stepStack.getChildren().get(i);
            pane.setVisible(i == index);
            pane.setManaged(i == index);
        }
        for (int i = 0; i < chips.length; i++) {
            chips[i].getStyleClass().removeAll("active", "done");
            if (i < index) {
                chips[i].getStyleClass().add("done");
            } else if (i == index) {
                chips[i].getStyleClass().add("active");
            }
        }
        backButton.setDisable(index == 0);
        nextButton.setText(index == 3 ? "Finish" : "Next");
    }

    private void next() {
        clearError();
        switch (step) {
            case 0 -> {
                if (!validateStep0()) return;
                saveProfileFromFields();
            }
            case 1 -> {
                if (!validateStep1()) return;
                saveProfileFromFields();
            }
            case 2 -> {
                if (!validateStep2()) return;
                saveProfileFromFields();
                saveTags();
            }
            case 3 -> {
                saveDocuments();
                ViewNavigator.show(ViewNavigator.DISCOVERY_VIEW);
                return;
            }
            default -> { }
        }
        showStep(step + 1);
    }

    // ------------------------------------------------------------------
    // Validation + persistence
    // ------------------------------------------------------------------

    private boolean validateStep0() {
        if (isBlank(firstNameField) || isBlank(lastNameField)) {
            return fail("Enter your first and last name.");
        }
        if (isBlank(schoolNameField)) {
            return fail("Enter your school name.");
        }
        if (schoolTypeCombo.getValue() == null) {
            return fail("Select your school type (public or private).");
        }
        if (programCombo.getValue() == null) {
            return fail("Select your degree program.");
        }
        if (yearCombo.getValue() == null) {
            return fail("Select your year level.");
        }
        Double gwa = parseGwa();
        if (gwa == null) {
            return false;
        }
        gwaPercent = gwa;
        return true;
    }

    private boolean validateStep1() {
        BigDecimal income = parseDecimal(incomeField, "Enter a valid annual family income (numbers only).");
        if (income == null) {
            return false;
        }
        if (income.signum() < 0) {
            return fail("Income cannot be negative.");
        }
        Integer household = parseInteger(householdField, "Enter a valid household size (whole number).");
        if (household == null) {
            return false;
        }
        if (household < 1) {
            return fail("Household size must be at least 1.");
        }
        incomeValue = income;
        householdValue = household;
        return true;
    }

    private boolean validateStep2() {
        if (isBlank(provinceField)) {
            return fail("Enter your province — some scholarships are residency-restricted.");
        }
        return true;
    }

    private Double parseGwa() {
        String text = gwaField.getText() == null ? "" : gwaField.getText().trim();
        double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            fail("Enter a valid GWA (e.g. 1.25 or 93.75).");
            return null;
        }
        if (GWA_FIVE_POINT.equals(gwaModeCombo.getValue())) {
            if (value < 1.0 || value > 5.0) {
                fail("A 5-point GWA must be between 1.00 and 5.00.");
                return null;
            }
            return GwaConverter.fivePointToPercent(value);
        }
        if (value < 0 || value > 100) {
            fail("A percentage GWA must be between 0 and 100.");
            return null;
        }
        return value;
    }

    private BigDecimal parseDecimal(TextField field, String error) {
        String text = field.getText() == null ? "" : field.getText().trim();
        if (text.isEmpty()) {
            fail(error);
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            fail(error);
            return null;
        }
    }

    private Integer parseInteger(TextField field, String error) {
        String text = field.getText() == null ? "" : field.getText().trim();
        if (text.isEmpty()) {
            fail(error);
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            fail(error);
            return null;
        }
    }

    private void saveProfileFromFields() {
        StudentProfile profile = new StudentProfile(
                studentId,
                userId,
                text(firstNameField),
                text(lastNameField),
                birthdatePicker.getValue(),
                sexCombo.getValue(),
                text(provinceField),
                text(cityField),
                text(schoolNameField),
                schoolTypeCombo.getValue(),
                programCombo.getValue() == null ? null : programCombo.getValue().programId(),
                yearCombo.getValue(),
                gwaPercent == null ? 0.0 : gwaPercent,
                failingCheck.isSelected(),
                incomeValue,
                householdValue);
        studentId = ServiceFactory.profileService().saveProfile(profile).studentId();
    }

    private void saveTags() {
        List<Integer> ids = tagChecks.stream()
                .filter(CheckBox::isSelected)
                .map(box -> (Integer) box.getUserData())
                .toList();
        ServiceFactory.profileService().saveTags(studentId, ids);
    }

    private void saveDocuments() {
        Map<Integer, Boolean> ready = new HashMap<>();
        for (CheckBox box : docChecks) {
            ready.put((Integer) box.getUserData(), box.isSelected());
        }
        ServiceFactory.profileService().saveDocuments(studentId, ready);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private boolean fail(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        return false;
    }

    private void clearError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private static boolean isBlank(TextField field) {
        return field.getText() == null || field.getText().isBlank();
    }

    private static String text(TextField field) {
        String value = field.getText();
        return value == null || value.isBlank() ? null : value.trim();
    }
}
