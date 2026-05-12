package com.phonebook.senior.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * SIM helpers that use [SubscriptionManager] as the source of truth. Going via
 * [TelecomManager.getCallCapablePhoneAccounts] is unreliable on third-party
 * apps (many OEM ROMs only populate that list for the default dialer).
 *
 * The stored setting is a string in the form "sub:<subscriptionId>" so callers
 * do not have to care about parcelables.
 */
object SimAccounts {

    data class SimOption(
        val subscriptionId: Int,
        val label: String,
        val slotIndex: Int
    ) {
        val serialized: String get() = "sub:$subscriptionId"
    }

    fun hasReadPhoneStatePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun listSimOptions(context: Context): List<SimOption> {
        if (!hasReadPhoneStatePermission(context)) return emptyList()
        val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return emptyList()
        return try {
            sm.activeSubscriptionInfoList.orEmpty().map { info ->
                val slot = info.simSlotIndex + 1
                val carrier = info.carrierName?.toString()?.trim().orEmpty()
                val display = info.displayName?.toString()?.trim().orEmpty()
                val extra = when {
                    display.isNotBlank() && carrier.isNotBlank() && display != carrier ->
                        "$display（$carrier）"
                    display.isNotBlank() -> display
                    carrier.isNotBlank() -> carrier
                    else -> ""
                }
                val label = if (extra.isBlank()) "卡$slot" else "卡$slot · $extra"
                SimOption(info.subscriptionId, label, info.simSlotIndex)
            }.sortedBy { it.slotIndex }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    /**
     * Resolve the stored setting into a [PhoneAccountHandle] that can be put
     * on an ACTION_CALL intent via [TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE].
     *
     * Returns null when the SIM is gone, the permission was revoked, or the
     * Android version / OEM does not expose a handle for the subscription.
     */
    @SuppressLint("MissingPermission")
    fun findHandle(context: Context, serialized: String): PhoneAccountHandle? {
        val subId = parseSubId(serialized) ?: return null
        if (!hasReadPhoneStatePermission(context)) return null

        // Preferred path: API 30+ exposes a direct sub -> handle mapping.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            try {
                val handle = tm?.createForSubscriptionId(subId)?.phoneAccountHandle
                if (handle != null) return handle
            } catch (_: Exception) {
                // fall through to best-effort match below
            }
        }

        // Fallback: try to match handle.id against the subscription id string.
        // Works on many OEM ROMs where the Telecom service uses the sub id as the
        // account id. Some ROMs use the ICCID instead; in that case we cannot
        // recover the handle and the caller falls back to the system chooser.
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            ?: return null
        return try {
            val candidates = telecom.callCapablePhoneAccounts.orEmpty()
            val subIdString = subId.toString()
            candidates.firstOrNull { it.id == subIdString }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun parseSubId(serialized: String): Int? {
        if (!serialized.startsWith("sub:")) return null
        return serialized.removePrefix("sub:").toIntOrNull()
    }

    fun subIdForSerialized(serialized: String): Int {
        return parseSubId(serialized) ?: -1
    }

    fun slotForSerialized(context: Context, serialized: String): Int {
        val subId = parseSubId(serialized) ?: return -1
        return listSimOptions(context).firstOrNull { it.subscriptionId == subId }?.slotIndex ?: -1
    }
}
