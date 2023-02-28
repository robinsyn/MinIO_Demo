package com.iredbook.pojo;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;

@Data
@Accessors(chain = true)
@Table(name = "sys_upload_task")
public class SysUploadTask {
    @Id
    private Long id;

    /**
     * 分片上传的uploadId
     */
    @Column(name = "upload_id")
    private String uploadId;

    /**
     * 文件唯一标识（md5）
     */
    @Column(name = "file_identifier")
    private String fileIdentifier;

    /**
     * 文件名
     */
    @Column(name = "file_name")
    private String fileName;

    /**
     * 所属桶名
     */
    @Column(name = "bucket_name")
    private String bucketName;

    /**
     * 文件的key
     */
    @Column(name = "object_key")
    private String objectKey;

    /**
     * 文件大小（byte）
     */
    @Column(name = "total_size")
    private Long totalSize;

    /**
     * 每个分片大小（byte）
     */
    @Column(name = "chunk_size")
    private Long chunkSize;

    /**
     * 分片数量
     */
    @Column(name = "chunk_num")
    private Integer chunkNum;

}