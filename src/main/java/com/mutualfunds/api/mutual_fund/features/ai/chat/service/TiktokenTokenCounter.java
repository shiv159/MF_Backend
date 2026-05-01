package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Component;

@Component
public class TiktokenTokenCounter implements TokenCounter {

    private final Encoding encoding;

    public TiktokenTokenCounter() {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        this.encoding = registry.getEncoding(EncodingType.CL100K_BASE);
    }

    @Override
    public int countTokens(String text) {
        if (text == null || text.isBlank()) {
            return 1;
        }
        try {
            return Math.max(1, encoding.countTokens(text));
        } catch (RuntimeException ex) {
            return Math.max(1, text.length() / 4);
        }
    }
}
