package org.tuoyu.resource.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.upload.FileType;
import org.tuoyu.common.core.upload.UploadFileValidator;
import org.tuoyu.common.core.utils.*;
import org.tuoyu.common.core.utils.file.FileUtils;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.common.oss.client.OssClient;
import org.tuoyu.common.oss.enums.AccessPolicy;
import org.tuoyu.common.oss.exception.S3StorageException;
import org.tuoyu.common.oss.factory.OssFactory;
import org.tuoyu.common.oss.model.Options;
import org.tuoyu.common.oss.model.PutObjectResult;
import org.tuoyu.resource.domain.SysOss;
import org.tuoyu.resource.domain.SysOssExt;
import org.tuoyu.resource.domain.bo.SysOssBo;
import org.tuoyu.resource.domain.vo.SysOssVo;
import org.tuoyu.resource.mapper.SysOssMapper;
import org.tuoyu.resource.config.UploadProperties;
import org.tuoyu.resource.service.ISysOssService;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 文件上传 服务层实现
 *
 * @author Lion Li
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysOssServiceImpl implements ISysOssService {

    private final SysOssMapper ossMapper;

    private final UploadProperties uploadProperties;

    /**
     * 查询OSS对象存储列表
     *
     * @param bo        OSS对象存储分页查询对象
     * @param pageParam 分页参数
     * @return 结果
     */
    @Override
    public PageResult<SysOssVo> queryPageList(SysOssBo bo, PageParam pageParam) {
        LambdaQueryWrapper<SysOss> lqw = buildQueryWrapper(bo);
        return PageResults.of(ossMapper.selectPage(pageParam.toPage(SysOss.class), lqw),
            oss -> matchingUrl(BeanMapper.map(oss, SysOssVo.class)));
    }

    /**
     * 根据一组 ossIds 获取对应的 SysOssVo 列表
     *
     * @param ossIds 一组文件在数据库中的唯一标识集合
     * @return 包含 SysOssVo 对象的列表
     */
    @Override
    public List<SysOssVo> listByIds(Collection<Long> ossIds) {
        SysOssServiceImpl ossService = SpringUtils.getAopProxy(this);
        List<Supplier<SysOssVo>> suppliers = ossIds.stream().map(id -> (Supplier<SysOssVo>) () -> {
            SysOssVo vo = ossService.getById(id);
            if (ObjectUtil.isNotNull(vo)) {
                try {
                    return this.matchingUrl(vo);
                } catch (Exception ignored) {
                    // 如果oss异常无法连接则将数据直接返回
                    return vo;
                }
            }
            return null;
        }).toList();
        List<SysOssVo> list = ThreadUtils.virtualSubmitAll(suppliers);
        list.removeAll(Collections.singleton(null));
        return list;
    }

    /**
     * 根据一组 ossIds 获取对应文件的 URL 列表
     *
     * @param ossIds 以逗号分隔的 ossId 字符串
     * @return 以逗号分隔的文件 URL 字符串
     */
    @Override
    public String selectUrlByIds(String ossIds) {
        List<Long> ids = StringUtils.splitTo(ossIds, Convert::toLong);
        SysOssServiceImpl ossService = SpringUtils.getAopProxy(this);
        List<Supplier<String>> suppliers = ids.stream().map(id -> (Supplier<String>) () -> {
            SysOssVo vo = ossService.getById(id);
            if (ObjectUtil.isNotNull(vo)) {
                try {
                    return this.matchingUrl(vo).getUrl();
                } catch (Exception ignored) {
                    // 如果oss异常无法连接则将数据直接返回
                    return vo.getUrl();
                }
            }
            return null;
        }).toList();
        List<String> list = ThreadUtils.virtualSubmitAll(suppliers);
        list.removeAll(Collections.singleton(null));
        return StringUtils.joinComma(list);
    }

    /**
     * 构造 OSS 文件列表查询条件。
     *
     * @param bo 文件筛选条件
     * @return 包含文件名、后缀、归属服务和创建时间区间的查询包装器
     */
    private LambdaQueryWrapper<SysOss> buildQueryWrapper(SysOssBo bo) {
        Map<String, Object> params = bo.getParams();
        return QueryBuilder.lambda(SysOss.class)
            .likeIfText(SysOss::getFileName, bo.getFileName())
            .likeIfText(SysOss::getOriginalName, bo.getOriginalName())
            .eqIfText(SysOss::getFileSuffix, bo.getFileSuffix())
            .eqIfText(SysOss::getUrl, bo.getUrl())
            .betweenParams(SysOss::getCreateTime, params, "beginCreateTime", "endCreateTime")
            .eqIfPresent(SysOss::getCreateBy, bo.getCreateBy())
            .eqIfText(SysOss::getService, bo.getService())
            .orderByAsc(SysOss::getOssId)
            .build();
    }

    /**
     * 根据 ossId 从缓存或数据库中获取 SysOssVo 对象
     *
     * @param ossId 文件在数据库中的唯一标识
     * @return SysOssVo 对象，包含文件信息
     */
    @Cacheable(cacheNames = CacheNames.SYS_OSS, key = "#ossId")
    @Override
    public SysOssVo getById(Long ossId) {
        return BeanMapper.map(ossMapper.selectById(ossId), SysOssVo.class);
    }

    /**
     * 文件下载方法，支持一次性下载完整文件
     *
     * @param ossId OSS对象ID
     */
    @Override
    public ResponseEntity<byte[]> download(Long ossId) {
        SysOssVo sysOss = SpringUtils.getAopProxy(this).getById(ossId);
        if (ObjectUtil.isNull(sysOss)) {
            throw new ServiceException("文件数据不存在!");
        }
        String percentEncodedFileName = FileUtils.percentEncode(sysOss.getOriginalName());
        // 媒体类型按扩展名由服务端认定，不使用存储中记录的值（旧数据可能是上传时客户端声明的 text/html 等）
        FileType fileType = FileType.ofExtension(sysOss.getFileSuffix());
        MediaType mediaType = fileType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(fileType.mediaType());
        return OssFactory.instance(sysOss.getService())
            .download(sysOss.getFileName(), (result, inputStream) -> {
                // 构建响应实体
                return ResponseEntity.ok()
                    .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition,download-filename")
                    // 禁止浏览器按内容猜测类型
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=%s;filename*=utf-8''%s".formatted(percentEncodedFileName, percentEncodedFileName))
                    .header("download-filename", percentEncodedFileName)
                    .contentType(mediaType)
                    .contentLength(result.size())
                    .body(IoUtil.readBytes(inputStream));
            });

    }

    /**
     * 上传 MultipartFile 到对象存储服务，并保存文件信息到数据库
     * <p>
     * 先按白名单与文件头校验；对象键的扩展名与存储的媒体类型都取校验后的值，不采信客户端声明的 Content-Type
     *
     * @param file 要上传的 MultipartFile 对象
     * @return 上传成功后的 SysOssVo 对象，包含文件信息
     * @throws ServiceException 文件为空、类型不允许、内容与扩展名不符，或上传过程中发生异常
     */
    @Override
    public SysOssVo upload(MultipartFile file, SysOssExt ossExt) {
        if (ObjectUtil.isNull(file) || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        UploadFileValidator.ValidatedFile validated = uploadProperties.validator().validate(file.getOriginalFilename(), file);
        String suffix = "." + validated.extension();
        OssClient instance = defaultStorage();
        String pathKey = instance.buildPathKey(TenantHelper.currentTenantId(), suffix);
        try (InputStream inputStream = file.getInputStream()) {
            PutObjectResult result = instance.upload(pathKey, inputStream, file.getSize(), Options.builder().setContentType(validated.mediaType()));
            ossExt = ossExt == null ? new SysOssExt() : ossExt;
            ossExt.setFileSize(file.getSize());
            ossExt.setContentType(validated.mediaType());
            // 保存文件信息
            return buildResultEntity(validated.filename(), suffix, instance.clientId(), result, ossExt);
        } catch (IOException e) {
            throw new ServiceException(e.getMessage());
        } catch (S3StorageException e) {
            throw storageUnavailable(instance.clientId(), e);
        }
    }

    @Override
    public SysOssVo upload(String originalName, byte[] content) {
        if (content == null || content.length == 0) {
            throw new ServiceException("上传文件不能为空");
        }
        ByteArrayResource source = new ByteArrayResource(content);
        UploadFileValidator.ValidatedFile validated = uploadProperties.validator().validate(originalName, source);
        String suffix = "." + validated.extension();
        OssClient instance = defaultStorage();
        String pathKey = instance.buildPathKey(TenantHelper.currentTenantId(), suffix);
        try (InputStream inputStream = source.getInputStream()) {
            PutObjectResult result = instance.upload(pathKey, inputStream, content.length, Options.builder().setContentType(validated.mediaType()));
            SysOssExt ossExt = new SysOssExt();
            ossExt.setFileSize((long) content.length);
            ossExt.setContentType(validated.mediaType());
            return buildResultEntity(validated.filename(), suffix, instance.clientId(), result, ossExt);
        } catch (IOException e) {
            throw new ServiceException(e.getMessage());
        } catch (S3StorageException e) {
            throw storageUnavailable(instance.clientId(), e);
        }
    }

    /**
     * 上传文件到对象存储服务，并保存文件信息到数据库
     *
     * @param file 要上传的文件对象
     * @return 上传成功后的 SysOssVo 对象，包含文件信息
     */
    @Override
    public SysOssVo upload(File file, SysOssExt ossExt) {
        if (ObjectUtil.isNull(file) || !file.isFile() || file.length() <= 0) {
            throw new ServiceException("上传文件不能为空");
        }
        UploadFileValidator.ValidatedFile validated = uploadProperties.validator().validate(file.getName(), new FileSystemResource(file));
        String suffix = "." + validated.extension();
        OssClient instance = defaultStorage();
        String pathKey = instance.buildPathKey(TenantHelper.currentTenantId(), suffix);
        PutObjectResult result;
        try {
            result = instance.upload(pathKey, file, Options.builder().setContentType(validated.mediaType()));
        } catch (S3StorageException e) {
            throw storageUnavailable(instance.clientId(), e);
        }
        SysOssExt ext1 = ossExt == null ? new SysOssExt() : ossExt;
        ext1.setFileSize(result.size());
        ext1.setContentType(validated.mediaType());
        // 保存文件信息
        return buildResultEntity(validated.filename(), suffix, instance.clientId(), result, ext1);
    }

    /**
     * 默认的存储客户端；存储配置不完整（缺少地址、桶等）时与访问失败同样处理
     */
    private static OssClient defaultStorage() {
        try {
            return OssFactory.instance();
        } catch (S3StorageException e) {
            throw storageUnavailable("默认", e);
        }
    }

    /**
     * 对象存储访问失败（地址、密钥、桶配置有误或服务不可达）：原因中可能有存储地址等内部信息，只返回错误编号，详细信息记在日志中
     */
    private static ServiceException storageUnavailable(String configKey, S3StorageException e) {
        String errorId = RandomUtil.randomNumbers(8);
        log.warn("文件存储 [{}] 访问失败, 错误编号: {}", configKey, errorId, e);
        return new ServiceException("文件存储服务访问失败，请检查存储配置 [错误编号: " + errorId + "]");
    }

    /**
     * 组装上传结果并持久化文件元数据。
     *
     * @param originalfileName 原始文件名
     * @param suffix           文件后缀
     * @param configKey        存储配置标识
     * @param result           上传结果
     * @param ext1             扩展属性对象
     * @return 持久化后的文件信息视图
     */
    @NotNull
    private SysOssVo buildResultEntity(String originalfileName, String suffix, String configKey, PutObjectResult result, SysOssExt ext1) {
        SysOss oss = new SysOss();
        oss.setUrl(result.url());
        oss.setFileSuffix(suffix);
        oss.setFileName(result.key());
        oss.setOriginalName(originalfileName);
        oss.setService(configKey);
        oss.setExt1(JsonUtils.toJsonString(ext1));
        ossMapper.insert(oss);
        SysOssVo sysOssVo = BeanMapper.map(oss, SysOssVo.class);
        return this.matchingUrl(sysOssVo);
    }

    /**
     * 新增OSS对象存储
     *
     * @param bo SysOssBo 对象，包含待插入的数据；成功后回填 ossId
     */
    @Override
    public void insertByBo(SysOssBo bo) {
        SysOss oss = BeanUtil.toBean(bo, SysOss.class);
        if (ossMapper.insert(oss) <= 0) {
            throw new ServiceException("新增文件记录失败");
        }
        bo.setOssId(oss.getOssId());
    }

    /**
     * 删除OSS对象存储
     *
     * @param ids     OSS对象ID串
     * @param isValid 判断是否需要校验
     * @throws ServiceException 文件数据不存在
     */
    @Override
    public void deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            // 做一些业务上的校验,判断是否需要校验
        }
        List<SysOss> list = ossMapper.selectByIds(ids);
        for (SysOss sysOss : list) {
            OssFactory.instance(sysOss.getService()).delete(sysOss.getFileName());
        }
        if (ossMapper.deleteByIds(ids) <= 0) {
            throw new ServiceException("文件数据不存在!");
        }
    }

    /**
     * 桶类型为 private 的URL 修改为临时URL时长为120s
     *
     * @param oss OSS对象
     * @return oss 匹配Url的OSS对象
     */
    private SysOssVo matchingUrl(SysOssVo oss) {
        OssClient instance = OssFactory.instance(oss.getService());
        // 仅修改桶类型为 private 的URL，临时URL时长为120s
        if (instance.verifyConfig(config -> AccessPolicy.PRIVATE.equals(config.accessControlPolicyConfig().accessPolicy()))) {
            oss.setUrl(instance.presignGetUrl(oss.getFileName(), Duration.ofSeconds(120)));
        }
        return oss;
    }

}
