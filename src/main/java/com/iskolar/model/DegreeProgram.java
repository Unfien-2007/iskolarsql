package com.iskolar.model;

/** Row projection of {@code degree_programs}. */
public record DegreeProgram(
        int programId,
        String programCode,
        String programName,
        FieldCategory fieldCategory) {

    /** {@code "BSIT — BS Information Technology"} for combo boxes and cards. */
    public String displayLabel() {
        return programCode + " — " + programName;
    }
}
