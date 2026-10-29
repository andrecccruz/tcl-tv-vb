package br.tvtcl.inicio;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.TextView;

/** Tela "Ajustes do início": fundo animado, quantidade de partículas e uso como tela inicial. */
public class SettingsActivity extends Activity {

    private static final int[] PARTICLE_NAMES = {
            R.string.particles_few, R.string.particles_normal, R.string.particles_many,
    };

    private TextView particles;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.scaled(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Switch animated = findViewById(R.id.animated_background);
        animated.setChecked(Prefs.animatedBackground(this));
        animated.setOnCheckedChangeListener((button, checked) -> Prefs.setAnimatedBackground(this, checked));
        animated.requestFocus();

        particles = findViewById(R.id.particles);
        showParticles();
        particles.setOnClickListener(v -> {
            Prefs.setParticles(this, (Prefs.particles(this) + 1) % PARTICLE_NAMES.length);
            showParticles();
        });

        // O ADB não pode ligar componentes de um app ("Shell cannot change component state"),
        // mas o próprio app pode ligar o seu alias de tela inicial.
        ComponentName home = new ComponentName(this, MainActivity.class.getPackage().getName() + ".HomeAlias");
        PackageManager pm = getPackageManager();
        Switch useAsHome = findViewById(R.id.use_as_home);
        useAsHome.setChecked(pm.getComponentEnabledSetting(home) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        useAsHome.setOnCheckedChangeListener((button, checked) -> pm.setComponentEnabledSetting(home,
                checked ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP));
    }

    private void showParticles() {
        particles.setText(getString(R.string.particles, getString(PARTICLE_NAMES[Prefs.particles(this)])));
    }
}
