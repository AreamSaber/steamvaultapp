package com.example.steam_vault_app.feature.steamsession

import org.json.JSONObject
import org.json.JSONTokener

internal data class SteamWebLoginAssistRequest(
    val requestId: Int,
    val stage: SteamWebLoginStage,
    val primaryValue: String,
    val secondaryValue: String? = null,
)

internal data class SteamWebLoginAssistResult(
    val success: Boolean,
    val submitted: Boolean,
    val matchedField: String?,
    val message: String?,
)

internal object SteamWebLoginAssist {
    fun supportsStage(stage: SteamWebLoginStage): Boolean {
        return stage == SteamWebLoginStage.WAITING_FOR_CREDENTIALS ||
            stage == SteamWebLoginStage.EMAIL_CODE_REQUIRED ||
            stage == SteamWebLoginStage.TWO_FACTOR_REQUIRED
    }

    fun buildJavascript(request: SteamWebLoginAssistRequest): String {
        if (request.stage == SteamWebLoginStage.WAITING_FOR_CREDENTIALS) {
            return buildCredentialJavascript(request)
        }

        val keywords = when (request.stage) {
            SteamWebLoginStage.EMAIL_CODE_REQUIRED -> {
                listOf("emailauth", "email", "guard", "code", "otp")
            }

            SteamWebLoginStage.TWO_FACTOR_REQUIRED -> {
                listOf("twofactor", "authcode", "guard", "code", "otp")
            }

            else -> emptyList()
        }
        val keywordsJson = keywords.joinToString(
            separator = ",",
            prefix = "[",
            postfix = "]",
        ) { keyword ->
            JSONObject.quote(keyword)
        }
        val submitHintsJson = listOf(
            "sign in",
            "continue",
            "submit",
            "verify",
            "登录",
            "继续",
            "验证",
        ).joinToString(
            separator = ",",
            prefix = "[",
            postfix = "]",
        ) { hint ->
            JSONObject.quote(hint)
        }
        val codeJson = JSONObject.quote(request.primaryValue.trim())
        return """
            (function() {
                const code = $codeJson;
                const keywords = $keywordsJson;
                const submitHints = $submitHintsJson;

                if (!code) {
                    return JSON.stringify({
                        success: false,
                        submitted: false,
                        matchedField: null,
                        message: "empty_code"
                    });
                }

                const visibilityFilter = (element) => {
                    if (!element) return false;
                    if (!element.isConnected) return false;
                    let current = element;
                    while (current) {
                        const style = window.getComputedStyle(current);
                        if (style) {
                            if (style.display === "none" || style.visibility === "hidden") {
                                return false;
                            }
                            if (current === element && style.opacity === "0") {
                                return false;
                            }
                        }
                        current = current.parentElement;
                    }
                    const rects = typeof element.getClientRects === "function"
                        ? element.getClientRects()
                        : null;
                    return !!(
                        (rects && rects.length > 0) ||
                        element.offsetWidth > 0 ||
                        element.offsetHeight > 0
                    );
                };

                const interactiveFilter = (element) => {
                    if (!element) return false;
                    const type = (element.type || "").toLowerCase();
                    if (type === "hidden" || type === "submit" || type === "button") {
                        return false;
                    }
                    if (element.disabled) return false;
                    if ((element.getAttribute("aria-disabled") || "").toLowerCase() === "true") {
                        return false;
                    }
                    return visibilityFilter(element);
                };

                const describe = (element) => [
                    element.name || "",
                    element.id || "",
                    element.placeholder || "",
                    element.getAttribute("autocomplete") || "",
                    element.type || "",
                    element.getAttribute("aria-label") || ""
                ].join(" ").toLowerCase();

                const setElementValue = (element, value) => {
                    const prototype = element instanceof HTMLTextAreaElement
                        ? HTMLTextAreaElement.prototype
                        : element instanceof HTMLInputElement
                        ? HTMLInputElement.prototype
                        : Object.getPrototypeOf(element);
                    const descriptor = prototype
                        ? Object.getOwnPropertyDescriptor(prototype, "value")
                        : null;
                    if (descriptor && typeof descriptor.set === "function") {
                        descriptor.set.call(element, value);
                    } else {
                        element.value = value;
                    }
                };

                const dispatchTextInputEvents = (element) => {
                    const inputEvent = typeof InputEvent === "function"
                        ? new InputEvent("input", {
                            bubbles: true,
                            composed: true,
                            inputType: "insertText",
                            data: element.value
                        })
                        : new Event("input", { bubbles: true });
                    element.dispatchEvent(inputEvent);
                    element.dispatchEvent(new Event("change", { bubbles: true }));
                    element.dispatchEvent(new Event("blur", { bubbles: true }));
                };

                const inputs = Array.from(document.querySelectorAll("input, textarea"));
                const candidates = inputs.filter((element) => {
                    if (!interactiveFilter(element)) return false;
                    const description = describe(element);
                    return keywords.some((keyword) => description.includes(keyword));
                });

                const target = candidates[0] || null;
                if (!target) {
                    return JSON.stringify({
                        success: false,
                        submitted: false,
                        matchedField: null,
                        message: "no_matching_field"
                    });
                }

                target.focus();
                setElementValue(target, code);
                dispatchTextInputEvents(target);

                let submitted = false;
                const form = target.form || document.querySelector("form");
                if (form) {
                    const submitScope = form || document;
                    const buttons = Array.from(
                        submitScope.querySelectorAll("button, input[type='submit']")
                    ).filter(visibilityFilter);
                    const matchingButtons = buttons.filter((element) => {
                        const text = (
                            (element.innerText || element.value || element.textContent || "") +
                            " " +
                            describe(element)
                        ).toLowerCase();
                        return submitHints.some((hint) => text.includes(hint));
                    });
                    const submitButton = matchingButtons.find(interactiveFilter)
                        || buttons.find(interactiveFilter)
                        || null;

                    if (!submitButton && matchingButtons.some((element) => !interactiveFilter(element))) {
                        return JSON.stringify({
                            success: true,
                            submitted: false,
                            matchedField: target.name || target.id || "",
                            message: "submit_not_ready"
                        });
                    }

                    if (submitButton && typeof submitButton.click === "function") {
                        submitButton.click();
                        submitted = true;
                    } else if (typeof form.requestSubmit === "function") {
                        form.requestSubmit();
                        submitted = true;
                    } else if (typeof form.submit === "function") {
                        form.submit();
                        submitted = true;
                    }
                }

                return JSON.stringify({
                    success: true,
                    submitted: submitted,
                    matchedField: target.name || target.id || "",
                    message: submitted ? "filled_and_submitted" : "filled_only"
                });
            })();
        """.trimIndent()
    }

    private fun buildCredentialJavascript(request: SteamWebLoginAssistRequest): String {
        val accountJson = JSONObject.quote(request.primaryValue.trim())
        val passwordJson = JSONObject.quote(request.secondaryValue.orEmpty().trim())
        val accountKeywordsJson = listOf(
            "accountname",
            "account",
            "username",
            "login",
            "email",
            "steamid",
        ).joinToString(
            separator = ",",
            prefix = "[",
            postfix = "]",
        ) { keyword ->
            JSONObject.quote(keyword)
        }
        val passwordKeywordsJson = listOf(
            "password",
            "pass",
            "passwd",
        ).joinToString(
            separator = ",",
            prefix = "[",
            postfix = "]",
        ) { keyword ->
            JSONObject.quote(keyword)
        }
        val submitHintsJson = listOf(
            "sign in",
            "login",
            "submit",
            "continue",
            "登录",
            "继续",
        ).joinToString(
            separator = ",",
            prefix = "[",
            postfix = "]",
        ) { hint ->
            JSONObject.quote(hint)
        }
        return """
            (function() {
                const account = $accountJson;
                const password = $passwordJson;
                const accountKeywords = $accountKeywordsJson;
                const passwordKeywords = $passwordKeywordsJson;
                const submitHints = $submitHintsJson;

                if (!account) {
                    return JSON.stringify({
                        success: false,
                        submitted: false,
                        matchedField: null,
                        message: "empty_account"
                    });
                }
                if (!password) {
                    return JSON.stringify({
                        success: false,
                        submitted: false,
                        matchedField: null,
                        message: "empty_password"
                    });
                }

                const visibilityFilter = (element) => {
                    if (!element) return false;
                    if (!element.isConnected) return false;
                    let current = element;
                    while (current) {
                        const style = window.getComputedStyle(current);
                        if (style) {
                            if (style.display === "none" || style.visibility === "hidden") {
                                return false;
                            }
                            if (current === element && style.opacity === "0") {
                                return false;
                            }
                        }
                        current = current.parentElement;
                    }
                    const rects = typeof element.getClientRects === "function"
                        ? element.getClientRects()
                        : null;
                    return !!(
                        (rects && rects.length > 0) ||
                        element.offsetWidth > 0 ||
                        element.offsetHeight > 0
                    );
                };

                const interactiveFilter = (element) => {
                    if (!element) return false;
                    const type = (element.type || "").toLowerCase();
                    if (type === "hidden" || type === "submit" || type === "button") {
                        return false;
                    }
                    if (element.disabled) return false;
                    if ((element.getAttribute("aria-disabled") || "").toLowerCase() === "true") {
                        return false;
                    }
                    return visibilityFilter(element);
                };

                const describe = (element) => [
                    element.name || "",
                    element.id || "",
                    element.placeholder || "",
                    element.getAttribute("autocomplete") || "",
                    element.type || "",
                    element.getAttribute("aria-label") || ""
                ].join(" ").toLowerCase();

                const setElementValue = (element, value) => {
                    const prototype = element instanceof HTMLTextAreaElement
                        ? HTMLTextAreaElement.prototype
                        : element instanceof HTMLInputElement
                        ? HTMLInputElement.prototype
                        : Object.getPrototypeOf(element);
                    const descriptor = prototype
                        ? Object.getOwnPropertyDescriptor(prototype, "value")
                        : null;
                    if (descriptor && typeof descriptor.set === "function") {
                        descriptor.set.call(element, value);
                    } else {
                        element.value = value;
                    }
                };

                const dispatchTextInputEvents = (element) => {
                    const inputEvent = typeof InputEvent === "function"
                        ? new InputEvent("input", {
                            bubbles: true,
                            composed: true,
                            inputType: "insertText",
                            data: element.value
                        })
                        : new Event("input", { bubbles: true });
                    element.dispatchEvent(inputEvent);
                    element.dispatchEvent(new Event("change", { bubbles: true }));
                    element.dispatchEvent(new Event("blur", { bubbles: true }));
                };

                const fillField = (element, value) => {
                    element.focus();
                    setElementValue(element, value);
                    dispatchTextInputEvents(element);
                };

                const inputs = Array.from(document.querySelectorAll("input, textarea"))
                    .filter(interactiveFilter);
                const accountField = inputs.find((element) => {
                    if ((element.type || "").toLowerCase() === "password") return false;
                    const description = describe(element);
                    return accountKeywords.some((keyword) => description.includes(keyword));
                }) || inputs.find((element) => {
                    const type = (element.type || "").toLowerCase();
                    return type == "text" || type == "email";
                }) || null;

                const passwordField = inputs.find((element) => {
                    const description = describe(element);
                    return (element.type || "").toLowerCase() === "password" ||
                        passwordKeywords.some((keyword) => description.includes(keyword));
                }) || null;

                if (!accountField) {
                    return JSON.stringify({
                        success: false,
                        submitted: false,
                        matchedField: null,
                        message: "no_account_field"
                    });
                }
                if (!passwordField) {
                    return JSON.stringify({
                        success: false,
                        submitted: false,
                        matchedField: accountField.name || accountField.id || "",
                        message: "no_password_field"
                    });
                }

                fillField(accountField, account);
                fillField(passwordField, password);

                let submitted = false;
                const form = passwordField.form || accountField.form || document.querySelector("form");
                if (form) {
                    const submitScope = form || document;
                    const buttons = Array.from(
                        submitScope.querySelectorAll("button, input[type='submit']")
                    ).filter(visibilityFilter);
                    const matchingButtons = buttons.filter((element) => {
                        const text = (
                            (element.innerText || element.value || element.textContent || "") +
                            " " +
                            describe(element)
                        ).toLowerCase();
                        return submitHints.some((hint) => text.includes(hint));
                    });
                    const submitButton = matchingButtons.find(interactiveFilter)
                        || buttons.find(interactiveFilter)
                        || null;

                    if (!submitButton && matchingButtons.some((element) => !interactiveFilter(element))) {
                        return JSON.stringify({
                            success: true,
                            submitted: false,
                            matchedField: [
                                accountField.name || accountField.id || "",
                                passwordField.name || passwordField.id || ""
                            ].filter(Boolean).join("|"),
                            message: "submit_not_ready"
                        });
                    }

                    if (submitButton && typeof submitButton.click === "function") {
                        submitButton.click();
                        submitted = true;
                    } else if (typeof form.requestSubmit === "function") {
                        form.requestSubmit();
                        submitted = true;
                    } else if (typeof form.submit === "function") {
                        form.submit();
                        submitted = true;
                    }
                }

                return JSON.stringify({
                    success: true,
                    submitted: submitted,
                    matchedField: [
                        accountField.name || accountField.id || "",
                        passwordField.name || passwordField.id || ""
                    ].filter(Boolean).join("|"),
                    message: submitted ? "filled_credentials_and_submitted" : "filled_credentials_only"
                });
            })();
        """.trimIndent()
    }

    fun parseJavascriptResult(rawValue: String?): SteamWebLoginAssistResult {
        val candidate = rawValue?.trim().orEmpty()
        if (candidate.isEmpty() || candidate == "null") {
            return SteamWebLoginAssistResult(
                success = false,
                submitted = false,
                matchedField = null,
                message = "empty_result",
            )
        }

        val decoded = runCatching {
            JSONTokener(candidate).nextValue()
        }.getOrNull() as? String
            ?: return SteamWebLoginAssistResult(
                success = false,
                submitted = false,
                matchedField = null,
                message = "invalid_result",
            )

        val payload = runCatching { JSONObject(decoded) }.getOrNull()
            ?: return SteamWebLoginAssistResult(
                success = false,
                submitted = false,
                matchedField = null,
                message = "invalid_payload",
            )

        return SteamWebLoginAssistResult(
            success = payload.optBoolean("success", false),
            submitted = payload.optBoolean("submitted", false),
            matchedField = payload.optString("matchedField").trim().ifEmpty { null },
            message = payload.optString("message").trim().ifEmpty { null },
        )
    }
}
