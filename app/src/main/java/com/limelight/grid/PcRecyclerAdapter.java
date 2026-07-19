package com.limelight.grid;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.limelight.PcView;
import com.limelight.R;
import com.limelight.grid.assets.ComputerScreenshotCache;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PcRecyclerAdapter extends RecyclerView.Adapter<PcRecyclerAdapter.PcViewHolder> {
    
    private Context context;
    private List<PcView.ComputerObject> computerList;
    private Map<String, PairingState> pairingStates = new HashMap<>();
    private PcView pcView;
    private final ComputerScreenshotCache screenshotCache;
    private static final String PAIRING_TAG = "Pairing";
    
    public PcRecyclerAdapter(Context context) {
        this.context = context;
        this.computerList = new ArrayList<>();
        this.screenshotCache = new ComputerScreenshotCache(context);
        if (context instanceof PcView) {
            this.pcView = (PcView) context;
        }
    }
    
    public void setComputers(List<PcView.ComputerObject> computers) {
        this.computerList.clear();
        this.computerList.addAll(computers);
        sortList();
        notifyDataSetChanged();
    }
    
    public void addComputer(PcView.ComputerObject computer) {
        computerList.add(computer);
        sortList();
        notifyDataSetChanged();
    }
    
    public boolean removeComputer(PcView.ComputerObject computer) {
        boolean removed = computerList.remove(computer);
        if (removed) {
            notifyDataSetChanged();
        }
        return removed;
    }
    
    private void sortList() {
        Collections.sort(computerList, new Comparator<PcView.ComputerObject>() {
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
    
    // 配对状态管理方法
    public void startPairing(PcView.ComputerObject computer, String pin) {
        String key = getComputerKey(computer);
        pairingStates.put(key, new PairingState(PairingStatus.PAIRING, pin));
        notifyDataSetChanged();
    }
    
    public void updatePairingStatus(PcView.ComputerObject computer, PairingStatus status) {
        String key = getComputerKey(computer);
        PairingState currentState = pairingStates.get(key);
        if (currentState != null) {
            pairingStates.put(key, new PairingState(status, currentState.pin));
            notifyDataSetChanged();
        }
    }
    
    public void clearPairingStatus(PcView.ComputerObject computer) {
        String key = getComputerKey(computer);
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
    
    private boolean isPolling = true;

    public void setPolling(boolean polling) {
        this.isPolling = polling;
        notifyDataSetChanged();
    }

    @Override
    public PcViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.pc_grid_item, parent, false);
        return new PcViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(PcViewHolder holder, int position) {
        PcView.ComputerObject computer = computerList.get(position);
        holder.bind(computer);
    }
    
    @Override
    public int getItemCount() {
        return computerList.size();
    }
    
    public PcView.ComputerObject getItem(int position) {
        return computerList.get(position);
    }
    
    public class PcViewHolder extends RecyclerView.ViewHolder {
        private ImageView gridImage;
        private ImageView gridOverlay;
        private ProgressBar gridSpinner;
        private TextView gridText;
        private TextView addressText;
        private TextView statusText;
        private View statusDot;
        private View placeholderIcon;
        private LinearLayout pairingInfoLayout;
        private TextView pairingStatusText;
        private Button cancelPairingButton;
        
        public PcViewHolder(View itemView) {
            super(itemView);
            gridImage = itemView.findViewById(R.id.grid_image);
            gridOverlay = itemView.findViewById(R.id.grid_overlay);
            gridSpinner = itemView.findViewById(R.id.grid_spinner);
            gridText = itemView.findViewById(R.id.grid_text);
            addressText = itemView.findViewById(R.id.pc_address_text);
            statusText = itemView.findViewById(R.id.pc_status_text);
            statusDot = itemView.findViewById(R.id.pc_status_dot);
            placeholderIcon = itemView.findViewById(R.id.pc_placeholder_icon);
            pairingInfoLayout = itemView.findViewById(R.id.pairing_info_layout);
            pairingStatusText = itemView.findViewById(R.id.pairing_status_text);
            cancelPairingButton = itemView.findViewById(R.id.cancel_pairing_button);
        }
        
        public void bind(PcView.ComputerObject computer) {
            Bitmap screenshot = screenshotCache.load(computer.details.uuid);
            if (screenshot != null) {
                gridImage.setImageBitmap(screenshot);
                if (placeholderIcon != null) {
                    placeholderIcon.setVisibility(View.GONE);
                }
            }
            else {
                gridImage.setImageDrawable(null);
                if (placeholderIcon != null) {
                    placeholderIcon.setVisibility(View.VISIBLE);
                }
            }
            
            // 为每个item单独设置长按监听器，使用自定义弹出菜单
            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Log.d("PcRecyclerAdapter", "Item长按事件触发，位置: " + getAdapterPosition());
                    // 使用自定义弹出菜单替代系统上下文菜单
                    if (context instanceof PcView) {
                        showCustomContextMenu(v, getAdapterPosition());
                    }
                    return true;
                }
            });
            
            // Dim the entire view if this specific address is not reachable
            if (computer.address != null && !computer.details.reachableAddresses.contains(computer.address)) {
                itemView.setAlpha(0.62f);
            } else {
                itemView.setAlpha(1.0f);
            }
            
            if (computer.details.state == ComputerDetails.State.UNKNOWN && isPolling) {
                gridSpinner.setVisibility(View.VISIBLE);
            } else {
                gridSpinner.setVisibility(View.INVISIBLE);
            }
            
            gridText.setText(computer.details.name);
            if (addressText != null) {
                addressText.setText(computer.address != null ? computer.address.address : "");
            }
            
            // 处理配对状态显示
            String key = getComputerKey(computer);
            PairingState pairingState = pairingStates.get(key);
            updateStatusViews(computer, pairingState, statusText, statusDot);
            
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
                
                pairingStatusText.setText(String.format(itemView.getContext().getString(R.string.pairing_status_pairing), pairingState.pin));
                cancelPairingButton.setText(R.string.pairing_cancel_button);
                
                // 设置取消按钮点击事件
                final PcView.ComputerObject finalComputer = computer;
                cancelPairingButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (pcView != null) {
                            pcView.cancelPairing(finalComputer);
                        }
                    }
                });
                
                // 配对中时隐藏锁图标
                gridOverlay.setVisibility(View.GONE);
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
                if (computer.details.state == ComputerDetails.State.OFFLINE) {
                    gridOverlay.setImageResource(R.drawable.ic_pc_offline);
                    gridOverlay.setVisibility(View.VISIBLE);
                } else if (computer.details.state == ComputerDetails.State.ONLINE &&
                        computer.details.pairState == PairingManager.PairState.NOT_PAIRED) {
                    gridOverlay.setImageResource(R.drawable.ic_lock);
                    gridOverlay.setAlpha(1.0f);
                    gridOverlay.setVisibility(View.VISIBLE);
                } else {
                    gridOverlay.setVisibility(View.GONE);
                }
            }
            
            // 设置点击事件
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (computer.details.state == ComputerDetails.State.OFFLINE ||
                        (computer.details.state == ComputerDetails.State.UNKNOWN && computer.address == null)) {
                        // Open the context menu if a PC is offline or refreshing (and we have no address to try)
                        itemView.showContextMenu();
                    } else if (computer.details.pairState != PairingManager.PairState.PAIRED) {
                        // Pair an unpaired machine by default
                        if (pcView != null) {
                            pcView.doPair(computer);
                        }
                    } else {
                        if (pcView != null) {
                            pcView.doAppList(computer, false, false);
                        }
                    }
                }
            });
            
        }
    }

    private void updateStatusViews(PcView.ComputerObject computer, PairingState pairingState, TextView statusText, View statusDot) {
        if (statusText == null || statusDot == null) {
            return;
        }

        if (pairingState != null && pairingState.status == PairingStatus.PAIRING) {
            statusText.setText(context.getString(R.string.pairing));
            statusDot.setBackgroundResource(R.drawable.pc_status_dot_warning);
        }
        else if (computer.details.state == ComputerDetails.State.UNKNOWN && isPolling) {
            statusText.setText(context.getString(R.string.searching_pc));
            statusDot.setBackgroundResource(R.drawable.pc_status_dot_warning);
        }
        else if (computer.details.state == ComputerDetails.State.OFFLINE) {
            statusText.setText(context.getString(R.string.pcview_menu_send_wol));
            statusDot.setBackgroundResource(R.drawable.pc_status_dot_offline);
        }
        else if (computer.details.pairState == PairingManager.PairState.NOT_PAIRED) {
            statusText.setText(context.getString(R.string.pcview_menu_pair_pc));
            statusDot.setBackgroundResource(R.drawable.pc_status_dot_warning);
        }
        else if (computer.details.runningGameId != 0) {
            statusText.setText(context.getString(R.string.applist_menu_resume));
            statusDot.setBackgroundResource(R.drawable.pc_status_dot_online);
        }
        else {
            statusText.setText(context.getString(R.string.pcview_menu_app_list));
            statusDot.setBackgroundResource(R.drawable.pc_status_dot_online);
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
    
    // 显示自定义上下文菜单
    private void showCustomContextMenu(View anchorView, int position) {
        Log.d("PcRecyclerAdapter", "显示自定义上下文菜单，位置: " + position);
        
        if (pcView == null || position < 0 || position >= computerList.size()) {
            Log.w("PcRecyclerAdapter", "无法显示菜单: pcView=" + pcView + ", position=" + position + ", listSize=" + computerList.size());
            return;
        }
        
        PcView.ComputerObject computer = computerList.get(position);
        if (computer == null) {
            Log.w("PcRecyclerAdapter", "无法获取计算机对象");
            return;
        }
        
        pcView.showComputerActions(computer);
        Log.d("PcRecyclerAdapter", "设备操作面板已显示");
    }

}
