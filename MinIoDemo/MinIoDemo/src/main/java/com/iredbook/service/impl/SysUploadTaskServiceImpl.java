package com.iredbook.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.iredbook.dto.TaskInfoDTO;
import com.iredbook.dto.TaskRecordDTO;
import com.iredbook.config.MinIOConfig;
import com.iredbook.bo.InitTaskParam;
import com.iredbook.mapper.SysUploadTaskMapper;
import com.iredbook.pojo.SysUploadTask;
import com.iredbook.service.SysUploadTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 分片上传-分片任务记录(SysUploadTask)表服务实现类
 *
 * @since 2022-08-22 17:47:31
 */
@Service("sysUploadTaskService")
public class SysUploadTaskServiceImpl implements SysUploadTaskService {

    // 预签名url过期时间(ms)
    private static final Long PRE_SIGN_URL_EXPIRE = 60 * 10 * 1000L;

    @Resource
    private AmazonS3 amazonS3;

    @Autowired
    private MinIOConfig minioProperties;

    @Autowired
    private SysUploadTaskMapper sysUploadTaskMapper;

    @Override
    public TaskInfoDTO getTaskInfo(String identifier) {
        SysUploadTask task = getByIdentifier(identifier);
        if (task == null) {
            return null;
        }
        TaskInfoDTO result = new TaskInfoDTO().setFinished(true).setTaskRecord(TaskRecordDTO.convertFromEntity(task)).setPath(getPath(task.getBucketName(), task.getObjectKey()));

        boolean doesObjectExist = amazonS3.doesObjectExist(task.getBucketName(), task.getObjectKey());
        if (!doesObjectExist) {
            // 未上传完，返回已上传的分片
            ListPartsRequest listPartsRequest = new ListPartsRequest(task.getBucketName(), task.getObjectKey(), task.getUploadId());
            PartListing partListing = amazonS3.listParts(listPartsRequest);
            result.setFinished(false).getTaskRecord().setExitPartList(partListing.getParts());
        }
        return result;
    }

    @Override
    public SysUploadTask getByIdentifier(String identifier) {
        Example taskExample = new Example(SysUploadTask.class);
        Example.Criteria criteria = taskExample.createCriteria();
        criteria.andEqualTo("fileIdentifier", identifier);
        SysUploadTask task = sysUploadTaskMapper.selectOneByExample(taskExample);
        return task;
    }

    @Override
    public TaskInfoDTO initTask(InitTaskParam param) {

        Date currentDate = new Date();
        String bucketName = minioProperties.getBucketName();
        String fileName = param.getFileName();
        String suffix = fileName.substring(fileName.lastIndexOf(".")+1, fileName.length());
        String key = StrUtil.format("{}/{}.{}", DateUtil.format(currentDate, "YYYY-MM-dd"), IdUtil.randomUUID(), suffix);
        String contentType = MediaTypeFactory.getMediaType(key).orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType(contentType);
        InitiateMultipartUploadResult initiateMultipartUploadResult = amazonS3
                .initiateMultipartUpload(new InitiateMultipartUploadRequest(bucketName, key).withObjectMetadata(objectMetadata));
        String uploadId = initiateMultipartUploadResult.getUploadId();

        SysUploadTask task = new SysUploadTask();
        int chunkNum = (int) Math.ceil(param.getTotalSize() * 1.0 / param.getChunkSize());
        // 获得全局唯一主键
        Long id = System.currentTimeMillis();
        task.setBucketName(minioProperties.getBucketName())
                .setChunkNum(chunkNum)
                .setChunkSize(param.getChunkSize())
                .setTotalSize(param.getTotalSize())
                .setFileIdentifier(param.getIdentifier())
                .setFileName(fileName)
                .setObjectKey(key)
                .setUploadId(uploadId)
                .setId(id);
        sysUploadTaskMapper.insert(task);
        return new TaskInfoDTO().setFinished(false).setTaskRecord(TaskRecordDTO.convertFromEntity(task)).setPath(getPath(bucketName, key));
    }

    @Override
    public String getPath(String bucket, String objectKey) {
        return StrUtil.format("{}/{}/{}", minioProperties.getEndpoint(), bucket, objectKey);
    }

    @Override
    public String genPreSignUploadUrl(String bucket, String objectKey, Map<String, String> params) {
        Date currentDate = new Date();
        Date expireDate = DateUtil.offsetMillisecond(currentDate, PRE_SIGN_URL_EXPIRE.intValue());
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectKey)
                .withExpiration(expireDate).withMethod(HttpMethod.PUT);
        if (params != null) {
            params.forEach((key, val) -> request.addRequestParameter(key, val));
        }
        URL preSignedUrl = amazonS3.generatePresignedUrl(request);
        return preSignedUrl.toString();
    }

    @Override
    public void merge(String identifier) {
        SysUploadTask task = getByIdentifier(identifier);
        if (task == null) {
            throw new RuntimeException("分片任务不存");
        }

        ListPartsRequest listPartsRequest = new ListPartsRequest(task.getBucketName(), task.getObjectKey(), task.getUploadId());
        PartListing partListing = amazonS3.listParts(listPartsRequest);
        List<PartSummary> parts = partListing.getParts();
        if (!task.getChunkNum().equals(parts.size())) {
            // 已上传分块数量与记录中的数量不对应，不能合并分块
            throw new RuntimeException("分片缺失，请重新上传");
        }
        CompleteMultipartUploadRequest completeMultipartUploadRequest = new CompleteMultipartUploadRequest()
                .withUploadId(task.getUploadId())
                .withKey(task.getObjectKey())
                .withBucketName(task.getBucketName())
                .withPartETags(parts.stream().map(partSummary -> new PartETag(partSummary.getPartNumber(), partSummary.getETag())).collect(Collectors.toList()));
        CompleteMultipartUploadResult result = amazonS3.completeMultipartUpload(completeMultipartUploadRequest);
    }
}
