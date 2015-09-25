package eu.kanade.mangafeed.ui.fragment;

import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import java.util.ArrayList;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.ui.activity.BaseActivity;

public class LibraryFragment extends Fragment {

    public static LibraryFragment newInstance() {
        LibraryFragment fragment = new LibraryFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public LibraryFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((BaseActivity) getActivity()).getSupportActionBar().setTitle(R.string.library_title);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        View view = inflater.inflate(R.layout.fragment_library, container, false);
        return view;
    }


    public class LocalManga {
        public String name;
        public String url;

        public LocalManga(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

}
