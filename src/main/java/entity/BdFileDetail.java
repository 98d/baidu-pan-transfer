package entity;

/**
 * @author by xinghaowen
 * @version 1.0
 * @classname BdFileDetail
 * @date 2021/2/7 11:48
 */
public class BdFileDetail {
    private String path;
    private String fsId;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getFsId() {
        return fsId;
    }

    public void setFsId(String fsId) {
        this.fsId = fsId;
    }

    @Override
    public String toString() {
        return "BdFileDetail{" +
                "path='" + path + '\'' +
                ", fsId='" + fsId + '\'' +
                '}';
    }
}
