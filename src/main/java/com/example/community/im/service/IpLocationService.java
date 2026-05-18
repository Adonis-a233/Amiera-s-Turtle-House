package com.example.community.im.service;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Header;
import org.lionsoul.ip2region.xdb.LongByteArray;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Version;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IpLocationService {

    private Searcher searcher;

    @PostConstruct
    public void init() {
        try {
            // ip2region 3.3.7 全内存模式正确调用链：
            // loadContentFromInputStream → loadHeaderFromBuffer → Version.fromHeader → newWithBuffer
            LongByteArray lba = Searcher.loadContentFromInputStream(
                    new ClassPathResource("ip2region_v4.xdb").getInputStream());
            Header header = Searcher.loadHeaderFromBuffer(lba);
            Version version = Version.fromHeader(header);
            searcher = Searcher.newWithBuffer(version, lba);
            log.info("ip2region 全内存模式初始化完成");
        } catch (Exception e) {
            log.error("ip2region 初始化失败: {}", e.getMessage());
        }
    }

    public String getLocation(String ip) {
        if (searcher == null || ip == null || ip.isBlank()) return "未知";
        try {
            String result = searcher.search(ip);
            String[] parts = result.split("\\|");
            StringBuilder location = new StringBuilder();
            if (parts.length > 2 && !"0".equals(parts[2])) {
                location.append(parts[2]);
            }
            if (parts.length > 3 && !"0".equals(parts[3])) {
                location.append(parts[3]);
            }
            return location.isEmpty() ? "未知" : location.toString();
        } catch (Exception e) {
            log.warn("IP {} 解析失败: {}", ip, e.getMessage());
            return "未知";
        }
    }

    public String getRealIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) {
            return xri.trim();
        }
        return request.getRemoteAddr();
    }
}
