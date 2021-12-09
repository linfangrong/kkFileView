package cn.keking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import cn.keking.model.ReturnResponse;
import cn.keking.service.FilePreview;
import cn.keking.utils.DownloadUtils;
import cn.keking.service.FileHandlerService;
import cn.keking.service.OfficeToPdfService;
import cn.keking.service.impl.OtherFilePreviewImpl;
import cn.keking.web.filter.BaseUrlFilter;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.DependsOn;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;

import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@Service
@DependsOn("configConstants")
public class PdfDownloadService {

    private static final String FILE_DIR = ConfigConstants.getFileDir();

    private final FileHandlerService fileHandlerService;
    private final OfficeToPdfService officeToPdfService;
    private final OtherFilePreviewImpl otherFilePreview;
    private final static Logger logger = LoggerFactory.getLogger(PdfDownloadService.class);

    public PdfDownloadService(FileHandlerService fileHandlerService, OfficeToPdfService officeToPdfService, OtherFilePreviewImpl otherFilePreview) {
        this.fileHandlerService = fileHandlerService;
        this.officeToPdfService = officeToPdfService;
        this.otherFilePreview = otherFilePreview;
    }

    public String pdfDownloadHandle(String url, Model model, FileAttribute fileAttribute, HttpServletResponse resp) {
        String baseUrl = BaseUrlFilter.getBaseUrl();
        String suffix = fileAttribute.getSuffix();
        String fileName = fileAttribute.getName();
        String fileMd5Name = fileAttribute.getHashName();
        String pdfName = fileMd5Name.substring(0, fileMd5Name.lastIndexOf(".") + 1) + "pdf";
        String outFilePath = FILE_DIR + pdfName;
        // 判断之前是否已转换过，如果转换过，直接返回，否则执行转换
        if (!fileHandlerService.listConvertedFiles().containsKey(pdfName) || !ConfigConstants.isCacheEnabled()) {
            if (suffix.equalsIgnoreCase("pdf") && (url != null && (!url.toLowerCase().startsWith("http") || (ConfigConstants.getDownloadOrigin() && !url.startsWith(baseUrl))))) {
                ReturnResponse<String> response = DownloadUtils.downLoad(fileAttribute, pdfName);
                if (response.isFailure()) {
                    return otherFilePreview.notSupportedFile(model, fileAttribute, response.getMsg());
                }
                if (ConfigConstants.isCacheEnabled()) {
                    fileHandlerService.addConvertedFile(pdfName, fileHandlerService.getRelativePath(outFilePath));
                }
            } else {
                String filePath;
                ReturnResponse<String> response = DownloadUtils.downLoad(fileAttribute, null);
                if (response.isFailure()) {
                    return otherFilePreview.notSupportedFile(model, fileAttribute, response.getMsg());
                }
                filePath = response.getContent();
                if (StringUtils.hasText(outFilePath)) {
                    officeToPdfService.openOfficeToPDF(filePath, outFilePath);
                    if (ConfigConstants.isCacheEnabled()) {
                        // 加入缓存
                        fileHandlerService.addConvertedFile(pdfName, fileHandlerService.getRelativePath(outFilePath));
                    }
                }
            }
        }
        model.addAttribute("pdfUrl", pdfName);

        try {
            resp.setHeader("Content-Type", "application/pdf");
            resp.setHeader("Content-Disposition", "attachment;filename="+fileName);
            OutputStream out = resp.getOutputStream();
            FileInputStream fileInputStream = new FileInputStream(outFilePath);
            byte[] buf = new byte[1024];
            int length = 0;
            while ((length=fileInputStream.read(buf)) != -1) {
                out.write(buf, 0, length);
            }
            fileInputStream.close();
        } catch (FileNotFoundException e) {
            logger.info("FileNotFoundException: {}", e);
            return otherFilePreview.notSupportedFile(model, fileAttribute, e.getMessage());
        } catch (IOException e) {
            logger.info("IOException: {}", e);
            return otherFilePreview.notSupportedFile(model, fileAttribute, e.getMessage());
        }
        return null;
    }
}
