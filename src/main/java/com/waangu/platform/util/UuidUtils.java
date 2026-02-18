package com.waangu.platform.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Utilitaires pour la manipulation sécurisée des UUID.
 * <p>
 * Fournit des méthodes de parsing qui gèrent proprement les cas d'erreur
 * au lieu de lancer des exceptions non gérées.
 * </p>
 */
public final class UuidUtils {

    private static final Logger log = LoggerFactory.getLogger(UuidUtils.class);

    private UuidUtils() {
        // Utility class - no instantiation
    }

    /**
     * Parse un UUID de manière sécurisée avec valeur par défaut.
     *
     * @param value        La chaîne à parser
     * @param defaultValue Valeur par défaut si parsing échoue
     * @return UUID parsé ou valeur par défaut
     */
    public static UUID parseOrDefault(String value, UUID defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format: '{}', using default", value);
            return defaultValue;
        }
    }

    /**
     * Parse un UUID de manière sécurisée, génère un nouveau si invalide.
     *
     * @param value La chaîne à parser
     * @return UUID parsé ou nouveau UUID aléatoire
     */
    public static UUID parseOrGenerate(String value) {
        return parseOrDefault(value, UUID.randomUUID());
    }

    /**
     * Parse un UUID de manière stricte (lance exception si invalide).
     *
     * @param value     La chaîne à parser
     * @param fieldName Nom du champ pour le message d'erreur
     * @return UUID parsé
     * @throws IllegalArgumentException si le format est invalide
     */
    public static UUID parseStrict(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldName + " has invalid UUID format: " + value, e);
        }
    }

    /**
     * Vérifie si une chaîne est un UUID valide.
     *
     * @param value La chaîne à vérifier
     * @return true si valide, false sinon
     */
    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value.trim());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
