package com.kghospital.formbuilder.domain.enums;

public enum FieldType {
    TEXT, TEXTAREA, NUMBER, DATE, DATETIME,
    DROPDOWN, MULTI_SELECT, CHECKBOX, RADIO,
    DRUG_LOOKUP,        // integrates with drug formulary
    PATIENT_LOOKUP,
    DIAGNOSIS_ICD,      // ICD-10 picker
    DOSAGE_UNIT,        // mg, mcg, mL etc
    FREQUENCY_PICKER,   // OD, BD, TDS, QID, PRN
    ROUTE_OF_ADMIN,     // IV, PO, IM, SC, SL
    FILE_UPLOAD, SIGNATURE, SECTION_HEADER, HIDDEN
}
