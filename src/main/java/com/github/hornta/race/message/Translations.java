package com.github.hornta.race.message;

import com.github.hornta.race.Racing;
import com.github.hornta.race.Util;
import com.google.common.base.Charsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class Translations {
  private static final Pattern ymlResources = Pattern.compile(".+\\.yml$");
  private static final Pattern translationResource = Pattern.compile("^translations/.+\\.yml$");
  private LanguageTranslation selectedLanguage;
  private Map<String, File> languageFiles = new HashMap<>();
  private File translationsDirectory;
  private JavaPlugin plugin;

  public Translations(JavaPlugin plugin) {
    this.plugin = plugin;
    translationsDirectory = new File(plugin.getDataFolder() + File.separator + "translations");
    saveDefaults();
    readLanguageFiles(translationsDirectory);
  }

  private Map<String, InputStream> getResourceTranslationStreams() {
    Map<String, InputStream> streams = new HashMap<>();

    ResourcesScanner resourcesScanner = new ResourcesScanner();
    Reflections reflections = new Reflections(null, resourcesScanner);
    Set<String> resourceList = reflections.getResources(ymlResources);

    for(String resource : resourceList) {
      String filename = resource.lastIndexOf('/') == -1 ? resource : resource.substring(resource.lastIndexOf('/') + 1);
      String path = resourcesScanner.getStore().get(filename).toArray(new String[0])[0];
      if(translationResource.matcher(path).matches()) {
        streams.put(path, getClass().getClassLoader().getResourceAsStream(path));
      }
    }

    return streams;
  }

  public boolean selectLanguage(String language) {
    if(!languageFiles.containsKey(language)) {
      Racing.logger().log(Level.SEVERE, "Couldn't find translation `" + language + "`");
      return false;
    }

    selectedLanguage = new LanguageTranslation(languageFiles.get(language));
    return selectedLanguage.load();
  }

  private void readLanguageFiles(File translationsDirectory) {
    File[] files = translationsDirectory.listFiles();

    if(files == null) {
      return;
    }

    for (File file : files) {
      if (!file.isFile()) {
        readLanguageFiles(file);
        continue;
      }

      if(!file.getName().endsWith(".yml")) {
        continue;
      }

      languageFiles.put(Util.getFilenameWithoutExtension(file), file);
    }
  }

  public LanguageTranslation getSelectedLanguage() {
    return selectedLanguage;
  }

  private boolean saveDefaults() {
    translationsDirectory.mkdirs();
    for(Map.Entry<String, InputStream> entry : getResourceTranslationStreams().entrySet()) {
      File destination = new File(plugin.getDataFolder(), entry.getKey());
      boolean result = saveTranslationResource(entry, destination);
      if(!result) {
        return false;
      }
    }

    return true;
  }

  private boolean saveTranslationResource(Map.Entry<String, InputStream> resource, File dest) {
    if(dest.exists()) {
      Racing.logger().log(Level.INFO, "Found existing translation file in " + dest.getName() + ".");

      YamlConfiguration destYaml = YamlConfiguration.loadConfiguration(dest);
      YamlConfiguration resourceYaml = YamlConfiguration.loadConfiguration(new InputStreamReader(resource.getValue(), Charsets.UTF_8));

      // delete keys not found in resource translation
      for(String key : destYaml.getKeys(true)) {
        if(!resourceYaml.contains(key)) {
          destYaml.set(key, null);
          Racing.logger().log(Level.INFO, "Deleted unused key from `" + key + "` from `" + dest.getName() + "`.");
        }
      }

      // add keys from resource not found in destination
      for(String key : resourceYaml.getKeys(true)) {
        if(!destYaml.contains(key)) {
          destYaml.set(key, resourceYaml.get(key));
          Racing.logger().log(Level.INFO, "Added missing key `" + key + "` to `" + dest.getName() + "`.");
        }
      }

      try {
        destYaml.save(dest);
      } catch (IOException ex) {
        Racing.logger().log(Level.SEVERE, ex.getMessage(), ex);
        return false;
      }
    } else {
      plugin.saveResource(resource.getKey(), false);
      Racing.logger().log(Level.INFO, "Saving new translation file to `" + dest.getName() + "`");
    }

    return true;
  }
}
