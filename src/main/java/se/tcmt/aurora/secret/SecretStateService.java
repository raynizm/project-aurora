// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.secret;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Secret state management service.
 * Stores secrets in ~/.aurora/secrets.json file.
 * Mirrors the reference plugin's MainThreadSecretStateShape.
 */
public final class SecretStateService implements com.intellij.openapi.Disposable {
    private static final Logger LOG = Logger.getInstance(SecretStateService.class);

    private final Gson gson;
    private final ReentrantLock lock;
    
    // Configuration file path
    private final File secretsDir;
    private final File secretsFile;

    public SecretStateService() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.lock = new ReentrantLock();
        
        // Set up secrets directory in user home
        String userHome = System.getProperty("user.home");
        this.secretsDir = new File(userHome, ".aurora");
        this.secretsFile = new File(secretsDir, "secrets.json");
        
        // Ensure the directory exists
        if (!secretsDir.exists()) {
            secretsDir.mkdirs();
            LOG.debug("Created secret storage directory: " + secretsDir.getAbsolutePath());
        }
        
        LOG.debug("Initialized SecretStateService with file: " + secretsFile.getAbsolutePath());
    }

    /**
     * Get a secret value by extension ID and key.
     */
    @Nullable
    public String getPassword(@NotNull String extensionId, @NotNull String key) {
        lock.lock();
        try {
            if (!secretsFile.exists()) {
                return null;
            }
            
            String jsonContent = new String(Files.readAllBytes(secretsFile.toPath()));
            if (jsonContent.isBlank()) {
                return null;
            }
            
            JsonObject jsonObject = JsonParser.parseString(jsonContent).getAsJsonObject();
            JsonObject extensionObject = jsonObject.getAsJsonObject(extensionId);
            if (extensionObject == null) {
                return null;
            }
            
            com.google.gson.JsonElement passwordElement = extensionObject.get(key);
            if (passwordElement == null || !passwordElement.isJsonPrimitive()) {
                return null;
            }
            
            return passwordElement.getAsString();
        } catch (Exception e) {
            LOG.warn("Failed to get secret: extensionId=" + extensionId + ", key=" + key, e);
            return null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Set a secret value.
     */
    public void setPassword(@NotNull String extensionId, @NotNull String key, 
                            @NotNull String value) {
        lock.lock();
        try {
            JsonObject jsonObject;
            if (secretsFile.exists() && Files.size(secretsFile.toPath()) > 0) {
                String jsonContent = new String(Files.readAllBytes(secretsFile.toPath()));
                if (!jsonContent.isBlank()) {
                    jsonObject = JsonParser.parseString(jsonContent).getAsJsonObject();
                } else {
                    jsonObject = new JsonObject();
                }
            } else {
                jsonObject = new JsonObject();
            }
            
            // Get or create extension object
            JsonObject extensionObject = jsonObject.getAsJsonObject(extensionId);
            if (extensionObject == null) {
                extensionObject = new JsonObject();
                jsonObject.add(extensionId, extensionObject);
            }
            
            // Set the password
            extensionObject.addProperty(key, value);
            
            // Write back to file
            String jsonString = gson.toJson(jsonObject);
            Files.write(secretsFile.toPath(), jsonString.getBytes());
            
            LOG.debug("Successfully set secret: extensionId=" + extensionId + ", key=" + key);
        } catch (Exception e) {
            LOG.error("Failed to set secret: extensionId=" + extensionId + ", key=" + key, e);
            throw new RuntimeException("Failed to set secret", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Delete a secret.
     */
    public void deletePassword(@NotNull String extensionId, @NotNull String key) {
        lock.lock();
        try {
            if (!secretsFile.exists()) {
                return;
            }
            
            String jsonContent = new String(Files.readAllBytes(secretsFile.toPath()));
            if (jsonContent.isBlank()) {
                return;
            }
            
            JsonObject jsonObject = JsonParser.parseString(jsonContent).getAsJsonObject();
            JsonObject extensionObject = jsonObject.getAsJsonObject(extensionId);
            if (extensionObject == null) {
                return;
            }
            
            // Remove the key
            extensionObject.remove(key);
            
            // If extension object is empty, remove the entire extension
            if (extensionObject.size() == 0) {
                jsonObject.remove(extensionId);
            }
            
            // Write back to file
            String jsonString = gson.toJson(jsonObject);
            Files.write(secretsFile.toPath(), jsonString.getBytes());
            
            LOG.debug("Successfully deleted secret: extensionId=" + extensionId + ", key=" + key);
        } catch (Exception e) {
            LOG.error("Failed to delete secret: extensionId=" + extensionId + ", key=" + key, e);
            throw new RuntimeException("Failed to delete secret", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check if a secret exists.
     */
    public boolean hasSecret(@NotNull String extensionId, @NotNull String key) {
        String value = getPassword(extensionId, key);
        return value != null;
    }

    /**
     * Get all secrets for an extension.
     */
    @Nullable
    public JsonObject getExtensionSecrets(@NotNull String extensionId) {
        lock.lock();
        try {
            if (!secretsFile.exists()) {
                return null;
            }
            
            String jsonContent = new String(Files.readAllBytes(secretsFile.toPath()));
            if (jsonContent.isBlank()) {
                return null;
            }
            
            JsonObject jsonObject = JsonParser.parseString(jsonContent).getAsJsonObject();
            return jsonObject.getAsJsonObject(extensionId);
        } catch (Exception e) {
            LOG.warn("Failed to get extension secrets: " + extensionId, e);
            return null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void dispose() {
        LOG.debug("Disposing SecretStateService");
        // JSON file storage doesn't require special resource disposal
    }
}
