package za.co.statecapture.android.ui.components

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.nativead.MediaView
import za.co.statecapture.android.util.AppConstants
import za.co.statecapture.android.R

@Composable
fun SupportAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var adLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val adLoader = AdLoader.Builder(context, AppConstants.ADMOB_NATIVE_UNIT_ID)
            .forNativeAd { ad ->
                nativeAd = ad
                adLoading = false
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    adLoading = false
                }
            })
            .build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (adLoading) {
            Text(
                "Loading sponsorship...",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(16.dp)
            )
        } else if (nativeAd != null) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    // Since we don't have an XML layout for the native ad, we'll create one programmatically
                    // or better, if we have R.layout.ad_native we could use it.
                    // For now, let's create a simple programmatic one to avoid res issues.
                    NativeAdView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        
                        val root = android.widget.LinearLayout(ctx).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            setPadding(16, 16, 16, 16)
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        }
                        
                        val headerLayout = android.widget.LinearLayout(ctx).apply {
                            orientation = android.widget.LinearLayout.HORIZONTAL
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        }
                        
                        val iconView = ImageView(ctx).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(100, 100).apply {
                                rightMargin = 16
                            }
                        }
                        headerLayout.addView(iconView)
                        
                        val textLayout = android.widget.LinearLayout(ctx).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        }
                        
                        val headline = TextView(ctx).apply {
                            textSize = 16f
                            setTextColor(android.graphics.Color.BLACK)
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            maxLines = 2
                        }
                        textLayout.addView(headline)
                        
                        val advertiser = TextView(ctx).apply {
                            textSize = 12f
                        }
                        textLayout.addView(advertiser)
                        headerLayout.addView(textLayout)
                        
                        root.addView(headerLayout)
                        
                        val mediaView = MediaView(ctx).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                400
                            ).apply {
                                topMargin = 16
                                bottomMargin = 16
                            }
                        }
                        root.addView(mediaView)
                        
                        val body = TextView(ctx).apply {
                            textSize = 14f
                            setPadding(0, 8, 0, 8)
                        }
                        root.addView(body)
                        
                        val callToAction = Button(ctx).apply {
                            setAllCaps(false)
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                topMargin = 16
                            }
                        }
                        root.addView(callToAction)
                        
                        addView(root)
                        
                        this.iconView = iconView
                        this.headlineView = headline
                        this.advertiserView = advertiser
                        this.mediaView = mediaView
                        this.bodyView = body
                        this.callToActionView = callToAction
                    }
                },
                update = { view ->
                    val ad = nativeAd ?: return@AndroidView
                    
                    (view.headlineView as? TextView)?.text = ad.headline
                    
                    val bodyView = view.bodyView as? TextView
                    if (ad.body == null) {
                        bodyView?.visibility = View.GONE
                    } else {
                        bodyView?.visibility = View.VISIBLE
                        bodyView?.text = ad.body
                    }
                    
                    val callToActionView = view.callToActionView as? Button
                    if (ad.callToAction == null) {
                        callToActionView?.visibility = View.GONE
                    } else {
                        callToActionView?.visibility = View.VISIBLE
                        callToActionView?.text = ad.callToAction
                    }
                    
                    val iconView = view.iconView as? ImageView
                    if (ad.icon == null) {
                        iconView?.visibility = View.GONE
                    } else {
                        iconView?.setImageDrawable(ad.icon?.drawable)
                        iconView?.visibility = View.VISIBLE
                    }
                    
                    val advertiserView = view.advertiserView as? TextView
                    if (ad.advertiser == null) {
                        advertiserView?.visibility = View.GONE
                    } else {
                        advertiserView?.text = ad.advertiser
                        advertiserView?.visibility = View.VISIBLE
                    }
                    
                    view.mediaView?.mediaContent = ad.mediaContent
                    
                    view.setNativeAd(ad)
                }
            )
        }
    }
}
