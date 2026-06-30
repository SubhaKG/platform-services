package com.kghospital.integrationregistry.dto;

/** Only the reference key is ever returned — never the value */
public record CredentialSubmissionResponse(String credentialRef, String rotatedAt) {}
