package com.example.mokodemo

import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.text.TextUtils
import android.view.Window
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mokodemo.adapter.BeaconResultAdapter
import com.example.mokodemo.entity.BeaconXInfo
import com.example.mokodemo.service.MokoService
import com.example.mokodemo.utils.BeaconXInfoParseableImpl
import com.example.mokodemo.utils.ToastUtils
import com.moko.support.MokoConstants
import com.moko.support.MokoSupport
import com.moko.support.callback.MokoScanDeviceCallback
import com.moko.support.entity.DeviceInfo
import com.moko.support.entity.OrderType
import com.moko.support.log.LogModule
import com.moko.support.task.OrderTask
import com.moko.support.task.OrderTaskResponse
import com.moko.support.utils.MokoUtils
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


/**
 * @Date 06/11/2020
 * @Author mitesh
 * @Description
 */

class MainActivity : BaseActivity(), MokoScanDeviceCallback {
    private var mMokoService: MokoService? = null

    private var beaconXInfoParseable: BeaconXInfoParseableImpl? = null
    private var mVerifyingDialog: ProgressDialog? = null
    private var mPassword: String = ""
    private var mLoadingDialog: ProgressDialog? = null

    private var beaconXInfoHashMap: HashMap<String, BeaconXInfo> = HashMap()
    private val beaconXInfos: ArrayList<BeaconXInfo> = ArrayList()

    private var animation: Animation? = null
    var filterName: String = ""
    var filterRssi = -127

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val intent = Intent(this, MokoService::class.java)
        startService(intent)
        bindService(intent, mServiceConnection, BIND_AUTO_CREATE)

        iv_refresh.setOnClickListener {
            if (!MokoSupport.getInstance().isBluetoothOpen) {
                // Bluetooth is not turned on, turn on Bluetooth
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, MokoConstants.REQUEST_CODE_ENABLE_BT)
                return@setOnClickListener
            }
            if (animation == null) {
                startScan()
            } else {
                mMokoService!!.mHandler.removeMessages(0)
                mMokoService!!.stopScanDevice()
            }
        }

        with(rvDevices) {
            layoutManager = LinearLayoutManager(context)
            itemAnimator = DefaultItemAnimator()
            adapter = BeaconResultAdapter(
                context, beaconXInfos,
                object : BeaconResultAdapter.ViewHolderClicks {
                    override fun onClickItem(position: Int) {
                    }
                }, false
            )
        }
    }


    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            mMokoService = (service as MokoService.LocalBinder).getService()
            // Register a broadcast receiver
            val filter = IntentFilter()
            filter.addAction(MokoConstants.ACTION_CONNECT_SUCCESS)
            filter.addAction(MokoConstants.ACTION_CONNECT_DISCONNECTED)
            filter.addAction(MokoConstants.ACTION_RESPONSE_SUCCESS)
            filter.addAction(MokoConstants.ACTION_RESPONSE_TIMEOUT)
            filter.addAction(MokoConstants.ACTION_RESPONSE_FINISH)
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            filter.priority = 100
            registerReceiver(mReceiver, filter)
            if (animation == null) {
                startScan()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {}
    }


    private var unLockResponse: String? = null
    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent != null) {
                val action = intent.action
                if (MokoConstants.ACTION_CONNECT_SUCCESS == action) {
                    dismissLoadingProgressDialog()
                    showVerifyingProgressDialog()
                    mMokoService!!.mHandler.postDelayed(
                        { mMokoService!!.sendOrder(mMokoService!!.lockState) },
                        1000
                    )
                }
                if (MokoConstants.ACTION_CONNECT_DISCONNECTED == action) {
                    dismissLoadingProgressDialog()
                    dismissVerifyProgressDialog()
                    ToastUtils.showToast(this@MainActivity, "Disconnected")
                    if (animation == null) {
                        startScan()
                    }
                }
                if (MokoConstants.ACTION_RESPONSE_TIMEOUT == action) {
                }
                if (MokoConstants.ACTION_RESPONSE_FINISH == action) {
                }
                if (MokoConstants.ACTION_RESPONSE_SUCCESS == action) {
                    val response =
                        intent.getSerializableExtra(MokoConstants.EXTRA_KEY_RESPONSE_ORDER_TASK) as OrderTaskResponse?
                    val orderType = response!!.orderType
                    val responseType = response.responseType
                    val value = response.responseValue
                    when (orderType) {
                        OrderType.lockState -> {
                            val valueStr = MokoUtils.bytesToHexString(value)
                            if ("00" == valueStr) {
                                if (!TextUtils.isEmpty(unLockResponse)) {
                                    dismissVerifyProgressDialog()
                                    unLockResponse = ""
                                    MokoSupport.getInstance().disConnectBle()
                                    ToastUtils.showToast(this@MainActivity, "Password error")
                                    if (animation == null) {
                                        startScan()
                                    }
                                } else {
                                    LogModule.i("Locked state, get unLock, unlock")
                                    mMokoService!!.sendOrder(mMokoService!!.unLock)
                                }
                            } else {
                                dismissVerifyProgressDialog()
                                LogModule.i("Successfully unlocked")
                                unLockResponse = ""
                                /* mSavedPassword = mPassword
                                val deviceInfoIntent = Intent(
                                    this@MainActivity,
                                    DeviceInfoActivity::class.java
                                )
                                deviceInfoIntent.putExtra(
                                    AppConstants.EXTRA_KEY_PASSWORD,
                                    mPassword
                                )
                                startActivityForResult(
                                    deviceInfoIntent,
                                    AppConstants.REQUEST_CODE_DEVICE_INFO
                                )*/
                            }
                        }
                        OrderType.unLock -> {
                            if (responseType == OrderTask.RESPONSE_TYPE_READ) {
                                unLockResponse = MokoUtils.bytesToHexString(value)
                                LogModule.i("Random number returned：$unLockResponse")
                                mMokoService!!.sendOrder(
                                    mMokoService!!.setConfigNotify(),
                                    mMokoService!!.setUnLock(mPassword, value)
                                )
                            }
                            if (responseType == OrderTask.RESPONSE_TYPE_WRITE) {
                                mMokoService!!.sendOrder(mMokoService!!.lockState)
                            }
                        }
                    }
                }
                if (BluetoothAdapter.ACTION_STATE_CHANGED == action) {
                    val blueState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0)
                    when (blueState) {
                        BluetoothAdapter.STATE_TURNING_OFF -> if (animation != null) {
                            mMokoService!!.mHandler.removeMessages(0)
                            mMokoService!!.stopScanDevice()
                        }
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                MokoConstants.REQUEST_CODE_ENABLE_BT -> if (animation == null) {
                    startScan()
                }
                AppConstants.REQUEST_CODE_DEVICE_INFO -> if (animation == null) {
                    startScan()
                }
            }
        } else {
            when (requestCode) {
                MokoConstants.REQUEST_CODE_ENABLE_BT ->                     // Bluetooth is not turned on
                    finish()
            }
        }
    }
    override fun onStartScan() {
        beaconXInfoHashMap.clear()
    }

    override fun onScanDevice(deviceInfo: DeviceInfo?) {
        val beaconXInfo: BeaconXInfo = beaconXInfoParseable?.parseDeviceInfo(deviceInfo) ?: return
        beaconXInfoHashMap.put(beaconXInfo.mac, beaconXInfo)
        updateDevices()
    }

    override fun onStopScan() {
        iv_refresh.clearAnimation()
        animation = null
        updateDevices()
    }

    private fun updateDevices() {
        beaconXInfos.clear()
        if (!TextUtils.isEmpty(filterName) || filterRssi != -127) {
            val beaconXInfosFilter: ArrayList<BeaconXInfo> =
                ArrayList(beaconXInfoHashMap.values)
            val iterator = beaconXInfosFilter.iterator()
            while (iterator.hasNext()) {
                val beaconXInfo = iterator.next()
                if (beaconXInfo!!.rssi > filterRssi) {
                    if (TextUtils.isEmpty(filterName)) {
                        continue
                    } else {
                        if (TextUtils.isEmpty(beaconXInfo.name) && TextUtils.isEmpty(
                                beaconXInfo.mac
                            )
                        ) {
                            iterator.remove()
                        } else if (TextUtils.isEmpty(beaconXInfo.name) && beaconXInfo.mac.toLowerCase()
                                .replace(":", "").contains(filterName.toLowerCase())
                        ) {
                            continue
                        } else if (TextUtils.isEmpty(beaconXInfo.mac) && beaconXInfo.name.toLowerCase()
                                .contains(filterName.toLowerCase())
                        ) {
                            continue
                        } else if (!TextUtils.isEmpty(beaconXInfo.name) && !TextUtils.isEmpty(
                                beaconXInfo.mac
                            ) && (beaconXInfo.name.toLowerCase()
                                .contains(filterName.toLowerCase()) || beaconXInfo.mac.toLowerCase()
                                .replace(":", "").contains(filterName.toLowerCase()))
                        ) {
                            continue
                        } else {
                            iterator.remove()
                        }
                    }
                } else {
                    iterator.remove()
                }
            }
            beaconXInfos.addAll(beaconXInfosFilter)
        } else {
            beaconXInfos.addAll(beaconXInfoHashMap.values)
        }
        Collections.sort(beaconXInfos, object : Comparator<BeaconXInfo?> {
            override fun compare(lhs: BeaconXInfo?, rhs: BeaconXInfo?): Int {
                if (lhs!!.rssi > rhs!!.rssi) {
                    return -1
                } else if (lhs.rssi < rhs.rssi) {
                    return 1
                }
                return 0
            }
        })
        rvDevices.adapter?.notifyDataSetChanged()
        tvDeviceNum.setText(String.format("Devices(%d)", beaconXInfos.size))
    }

    private fun showVerifyingProgressDialog() {
        mVerifyingDialog = ProgressDialog(this)
        mVerifyingDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        mVerifyingDialog!!.setCanceledOnTouchOutside(false)
        mVerifyingDialog!!.setCancelable(false)
        mVerifyingDialog!!.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        mVerifyingDialog!!.setMessage("Verifying...")
        if (!isFinishing && mVerifyingDialog != null && !mVerifyingDialog!!.isShowing()) {
            mVerifyingDialog!!.show()
        }
    }

    private fun dismissVerifyProgressDialog() {
        if (!isFinishing && mVerifyingDialog != null && mVerifyingDialog!!.isShowing()) {
            mVerifyingDialog!!.dismiss()
        }
    }

    private fun showLoadingProgressDialog() {
        mLoadingDialog = ProgressDialog(this@MainActivity)
        mLoadingDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        mLoadingDialog!!.setCanceledOnTouchOutside(false)
        mLoadingDialog!!.setCancelable(false)
        mLoadingDialog!!.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        mLoadingDialog!!.setMessage("Connecting...")
        if (!isFinishing && mLoadingDialog != null && !mLoadingDialog!!.isShowing()) {
            mLoadingDialog!!.show()
        }
    }

    private fun dismissLoadingProgressDialog() {
        if (!isFinishing && mLoadingDialog != null && mLoadingDialog!!.isShowing()) {
            mLoadingDialog!!.dismiss()
        }
    }

    private fun startScan() {
        if (!MokoSupport.getInstance().isBluetoothOpen) {
            // Bluetooth is not turned on, turn on Bluetooth
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, MokoConstants.REQUEST_CODE_ENABLE_BT)
            return
        }
        animation = AnimationUtils.loadAnimation(this, R.anim.rotate_refresh)
        iv_refresh.startAnimation(animation)
        beaconXInfoParseable = BeaconXInfoParseableImpl()
        mMokoService!!.startScanDevice(this)
        mMokoService!!.mHandler.postDelayed({ mMokoService!!.stopScanDevice() }, 1000 * 60)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(mReceiver)
        unbindService(mServiceConnection)
    }

}