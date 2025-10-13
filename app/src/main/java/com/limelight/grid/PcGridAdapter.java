package com.limelight.grid;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.limelight.PcView;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class PcGridAdapter extends GenericGridAdapter<PcView.ComputerObject> {

    // 跟踪配对状态
    private Map<String, PairingState> pairingStates = new HashMap<>();
    private PcView pcView;
    private static final String PAIRING_TAG = "Pairing";

    public PcGridAdapter(Context context, PreferenceConfiguration prefs) {
        super(context, getLayoutIdForPreferences(prefs));
        if (context instanceof PcView) {
            this.pcView = (PcView) context;
        }
    }

    private static int getLayoutIdForPreferences(PreferenceConfiguration prefs) {
        return R.layout.pc_grid_item;
    }

    public void updateLayoutWithPreferences(Context context, PreferenceConfiguration prefs) {
        // This will trigger the view to reload with the new layout
        setLayoutId(getLayoutIdForPreferences(prefs));
    }

    public void addComputer(PcView.ComputerObject computer) {
        itemList.add(computer);
        sortList();
    }

    private void sortList() {
        Collections.sort(itemList, new Comparator<PcView.ComputerObject>() {
            @Override
            public int compare(PcView.ComputerObject lhs, PcView.ComputerObject rhs) {
                int nameCmp = lhs.details.name.toLowerCase().compareTo(rhs.details.name.toLowerCase());
                if (nameCmp != 0) {
                    return nameCmp;
                }

                if (lhs.address != null && rhs.address != null) {
                    return lhs.address.toString().compareTo(rhs.address.toString());
                }
                else if (lhs.address != null) {
                    return 1;
                }
                else if (rhs.address != null) {
                    return -1;
                }
                else {
                    return 0;
                }
            }
        });
    }

    public boolean removeComputer(PcView.ComputerObject computer) {
        return itemList.remove(computer);
    }

    // 配对状态管理方法
    public void startPairing(PcView.ComputerObject computer, String pin) {
        String key = getComputerKey(computer);
        Log.i(PAIRING_TAG, "开始UI配对状态: " + computer.details.name + " PIN: " + pin);
        pairingStates.put(key, new PairingState(PairingStatus.PAIRING, pin));
        notifyDataSetChanged();
    }

    public void updatePairingStatus(PcView.ComputerObject computer, PairingStatus status) {
        String key = getComputerKey(computer);
        PairingState currentState = pairingStates.get(key);
        if (currentState != null) {
            Log.i(PAIRING_TAG, "更新UI配对状态: " + computer.details.name + " -> " + status);
            pairingStates.put(key, new PairingState(status, currentState.pin));
            notifyDataSetChanged();
        }
    }

    public void clearPairingStatus(PcView.ComputerObject computer) {
        String key = getComputerKey(computer);
        Log.i(PAIRING_TAG, "清除UI配对状态: " + computer.details.name);
        pairingStates.remove(key);
        notifyDataSetChanged();
    }

    public boolean isPairing(PcView.ComputerObject computer) {
        String key = getComputerKey(computer);
        PairingState state = pairingStates.get(key);
        return state != null && state.status == PairingStatus.PAIRING;
    }

    private String getComputerKey(PcView.ComputerObject computer) {
        return computer.details.uuid + "_" + (computer.address != null ? computer.address.toString() : "");
    }

    @Override
    public void populateView(View parentView, ImageView imgView, ProgressBar prgView, TextView txtView, ImageView overlayView, PcView.ComputerObject obj) {
        imgView.setImageResource(R.drawable.ic_computer);

        // 获取配对信息视图
        LinearLayout pairingInfoLayout = parentView.findViewById(R.id.pairing_info_layout);
        TextView pairingStatusText = parentView.findViewById(R.id.pairing_status_text);
        Button cancelPairingButton = parentView.findViewById(R.id.cancel_pairing_button);

        // Dim the entire view if this specific address is not reachable
        if (obj.address != null && !obj.details.reachableAddresses.contains(obj.address)) {
            parentView.setAlpha(0.4f);
        }
        else {
            parentView.setAlpha(1.0f);
        }

        if (obj.details.state == ComputerDetails.State.UNKNOWN) {
            prgView.setVisibility(View.VISIBLE);
        }
        else {
            prgView.setVisibility(View.INVISIBLE);
        }

        if (obj.address != null) {
            txtView.setText(obj.details.name + "\n" + obj.address.address);
        }
        else {
            txtView.setText(obj.details.name);
        }

        // 处理配对状态显示
        String key = getComputerKey(obj);
        PairingState pairingState = pairingStates.get(key);
        
        if (pairingState != null && pairingState.status == PairingStatus.PAIRING) {
            // 显示配对信息，带动画
            if (pairingInfoLayout.getVisibility() != View.VISIBLE) {
                pairingInfoLayout.setAlpha(0f);
                pairingInfoLayout.setVisibility(View.VISIBLE);
                pairingInfoLayout.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start();
            }
            
            pairingStatusText.setText(String.format(parentView.getContext().getString(R.string.pairing_status_pairing), pairingState.pin));
            cancelPairingButton.setText(R.string.pairing_cancel_button);
            
            // 设置取消按钮点击事件
            final PcView.ComputerObject finalObj = obj;
            cancelPairingButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.i(PAIRING_TAG, "用户点击取消配对按钮: " + finalObj.details.name);
                    if (pcView != null) {
                        pcView.cancelPairing(finalObj);
                    }
                }
            });
            
            // 配对中时隐藏锁图标
            overlayView.setVisibility(View.GONE);
        } else {
            // 隐藏配对信息，带动画
            if (pairingInfoLayout.getVisibility() == View.VISIBLE) {
                pairingInfoLayout.animate()
                    .alpha(0f)
                    .setDuration(100)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            pairingInfoLayout.setVisibility(View.GONE);
                        }
                    })
                    .start();
            } else {
                pairingInfoLayout.setVisibility(View.GONE);
            }
            
            // 恢复正常的覆盖图标显示
            if (obj.details.state == ComputerDetails.State.OFFLINE) {
                overlayView.setImageResource(R.drawable.ic_pc_offline);
                overlayView.setVisibility(View.VISIBLE);
            }
            // We must check if the status is exactly online and unpaired
            // to avoid colliding with the loading spinner when status is unknown
            else if (obj.details.state == ComputerDetails.State.ONLINE &&
                    obj.details.pairState == PairingManager.PairState.NOT_PAIRED) {
                overlayView.setImageResource(R.drawable.ic_lock);
                overlayView.setAlpha(1.0f);
                overlayView.setVisibility(View.VISIBLE);
            }
            else {
                overlayView.setVisibility(View.GONE);
            }
        }
    }

    // 配对状态枚举
    public enum PairingStatus {
        PAIRING,
        SUCCESS,
        FAILED,
        CANCELLED
    }

    // 配对状态类
    private static class PairingState {
        PairingStatus status;
        String pin;

        PairingState(PairingStatus status, String pin) {
            this.status = status;
            this.pin = pin;
        }
    }
}
