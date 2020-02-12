package org.graalvm.tools.lsp.definitions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class ExampleDefinition {
    public enum ProbeMode {
        DEFAULT,
        ALL,
        OFF
    }

    private String exampleName;
    private int functionStartLine;
    private String functionName;
    private List<ProbeDefinition> probes;
    private ProbeMode probeMode;
    private List<AssertionDefinition> assertions;
    private Object exampleResult;
    private int exampleDefinitionLine;
    private String uri;
    private String uniqueEmoji;

    private static final String[] emojis = {
            "🍄",
            "🍅",
            "🍆",
            "🍇",
            "🍈",
            "🍉",
            "🍊",
            "🍋",
            "🍌",
            "🍍",
            "🍎",
            "🍏",
            "🍐",
            "🍑",
            "🍒",
            "🍓",
            "🍔",
            "🍕",
            "🍖",
            "🍗",
            "🍘",
            "🍙",
            "🍚",
            "🍛",
            "🍜",
            "🍝",
            "🍞",
            "🍟",
            "🍠",
            "🍡",
            "🍢",
            "🍣",
            "🍤",
            "🍥",
            "🍦",
            "🍧",
            "🍨",
            "🍩",
            "🍪",
            "🍫",
            "🍬",
            "🍭",
            "🍮",
            "🍯",
            "🍰",
            "🍱",
            "🍲",
            "🍳",
            "🍴",
            "🍵",
            "🍶",
            "🍷",
            "🍸",
            "🍹",
            "🍺",
            "🍼",
            "🍽",
            "🍾",
            "🍿",
            "🎀",
            "🎁",
            "🎂",
    };

    public ExampleDefinition(String exampleName,
                             int functionStartLine,
                             String functionName,
                             int exampleDefinitionLine,
                             String uri,
                             ProbeMode probeMode) {
        this.exampleName = exampleName;
        this.functionStartLine = functionStartLine;
        this.functionName = functionName;
        this.probes = new ArrayList<>();
        this.probeMode = probeMode;
        this.assertions = new ArrayList<>();
        this.exampleDefinitionLine = exampleDefinitionLine;
        this.uri = uri;
    }

    public String getExampleName() {
        return exampleName;
    }

    public String getUniqueEmoji() {
        if (this.uniqueEmoji == null) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] encodedhash = digest.digest(getExampleName().getBytes(StandardCharsets.UTF_8));
                String hexHash = bytesToHex(encodedhash);
                long decimalHash = Long.parseLong(hexHash.substring(0, 8), 16);
                int emojiIndex = Math.toIntExact(decimalHash % emojis.length);
                this.uniqueEmoji = emojis[emojiIndex];
            } catch (NoSuchAlgorithmException e) {
                this.uniqueEmoji = "";
            }
        }

        return this.uniqueEmoji;
    }

    private String bytesToHex(byte[] hash) {
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public int getFunctionStartLine() {
        return functionStartLine;
    }

    public String getFunctionName() {
        return functionName;
    }

    public List<ProbeDefinition> getProbes() {
        return this.probes;
    }

    public ProbeMode getProbeMode() {
        return probeMode;
    }

    public List<AssertionDefinition> getAssertions() {
        return this.assertions;
    }

    public Object getExampleResult() {
        return exampleResult;
    }

    public void setExampleResult(Object exampleResult) {
        this.exampleResult = exampleResult;
    }

    public int getExampleDefinitionLine() {
        return exampleDefinitionLine;
    }

    public String getUri() {
        return uri;
    }
}