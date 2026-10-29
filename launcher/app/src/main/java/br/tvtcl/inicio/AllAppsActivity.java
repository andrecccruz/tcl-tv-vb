package br.tvtcl.inicio;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Grade com todos os apps instalados, em ordem alfabética. OK abre; segurar OK abre a tela do app nas Configurações. */
public class AllAppsActivity extends Activity {

    private final List<Apps.Entry> apps = new ArrayList<>();
    private BaseAdapter adapter;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.scaled(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_apps);

        LayoutInflater inflater = getLayoutInflater();
        adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return apps.size();
            }

            @Override
            public Object getItem(int position) {
                return apps.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View view, ViewGroup parent) {
                if (view == null) {
                    view = inflater.inflate(R.layout.item_app, parent, false);
                }
                Apps.Entry entry = apps.get(position);
                ((ImageView) view.findViewById(R.id.icon)).setImageBitmap(entry.tile);
                ((TextView) view.findViewById(R.id.label)).setText(entry.label);
                return view;
            }
        };

        GridView grid = findViewById(R.id.grid);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> Apps.open(this, apps.get(position).component));
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            Apps.openDetails(this, apps.get(position).component);
            return true;
        });
        grid.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Mesmo tamanho de bloco da tela principal, para reaproveitar os desenhos já prontos.
        apps.clear();
        apps.addAll(Apps.load(this, AppRowView.tilePx(this), false));
        adapter.notifyDataSetChanged();
    }
}
