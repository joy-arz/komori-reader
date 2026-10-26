package app.komori.data

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Resolve public source/CDN names without accepting ISP redirect certificates. */
object SourceDns : Dns {
    private val encrypted by lazy {
        DnsOverHttps.Builder()
            .client(OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build())
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1)), InetAddress.getByAddress(byteArrayOf(1, 0, 0, 1)))
            .includeIPv6(false)
            .build()
    }
    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname == "localhost" || hostname.endsWith(".local") || !hostname.contains('.'))
            return Dns.SYSTEM.lookup(hostname)
        return try { encrypted.lookup(hostname) }
        catch (e: UnknownHostException) { Dns.SYSTEM.lookup(hostname) }
    }
}
