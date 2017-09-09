package com.royale.royaleandroidexample;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link DepthCameraFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class DepthCameraFragment extends Fragment {

  public static ImageView amplitudeView;
  public static TextView debugTxt;

  public DepthCameraFragment() {
  }

  public static DepthCameraFragment newInstance() {
    DepthCameraFragment fragment = new DepthCameraFragment();
    return fragment;
  }

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    // Inflate the layout for this fragment
    View v = inflater.inflate(R.layout.fragment_depth_camera, container, false);
    amplitudeView = (ImageView) v.findViewById(R.id.imageViewAmplitude);
    debugTxt = (TextView) v.findViewById(R.id.txtDebugInfo);
    return v;
  }
}
