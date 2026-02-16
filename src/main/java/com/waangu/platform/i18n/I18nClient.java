package com.waangu.platform.i18n;

import java.util.Optional;

/**
 * Client interface for internationalization (i18n) translation services.
 * <p>
 * Provides methods to retrieve localized messages based on message keys and locale.
 * </p>
 *
 * @see com.waangu.platform.i18n.HttpI18nClient
 */
public interface I18nClient {

    void upsertSource(String key, String locale, String text);

    Optional<String> translate(String key, String locale);
}
