package com.microsoft.terraform;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TerraformOptions {
	private static final String SUBS_ID_ENV_NAME = "ARM_SUBSCRIPTION_ID", CLIENT_ID_ENV_NAME = "ARM_CLIENT_ID", SECRET_ENV_NAME = "ARM_CLIENT_SECRET", TENANT_ID_ENV_NAME = "ARM_TENANT_ID";

	private final Map<String, String> envVars = new HashMap<>();

	public String getArmSubscriptionId() {
		return getEnvVar(SUBS_ID_ENV_NAME);
	}

	public void setArmSubscriptionId(String armSubscriptionId) {
		setEnvVar(SUBS_ID_ENV_NAME, armSubscriptionId);
	}

	public String getArmClientId() {
		return getEnvVar(CLIENT_ID_ENV_NAME);
	}

	public void setArmClientId(String armClientId) {
		setEnvVar(CLIENT_ID_ENV_NAME, armClientId);
	}

	public String getArmClientSecret() {
		return getEnvVar(SECRET_ENV_NAME);
	}

	public void setArmClientSecret(String armClientSecret) {
		setEnvVar(SECRET_ENV_NAME, armClientSecret);
	}

	public String getArmTenantId() {
		return getEnvVar(TENANT_ID_ENV_NAME);
	}

	public void setArmTenantId(String armTenantId) {
		setEnvVar(TENANT_ID_ENV_NAME, armTenantId);
	}

	public String getEnvVar(String name) {
		return envVars.get(name);
	}

	public void setEnvVar(String name, String value) {
		envVars.put(name, value);
	}

	public Map<String, String> getEnvVars() {
		return Collections.unmodifiableMap(envVars);
	}
}
