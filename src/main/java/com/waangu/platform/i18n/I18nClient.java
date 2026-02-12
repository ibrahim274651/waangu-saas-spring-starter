package com.waangu.platform.i18n;

import java.util.Optional;

/**
 * Translation service client (chagpt). Implemented by HttpI18nClient.
 */
public interface I18nClient {

    void upsertSource(String key, String locale, String text);

    Optional<String> translate(String key, String locale);
}
