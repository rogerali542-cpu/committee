-- 地图选点经纬度（0723）：发起会议「从地图选点」（腾讯选点组件）回传坐标随建会落库，
-- 详情页「地图导航」有坐标时用精确打点（uri.amap.com/marker），无坐标退回关键词搜索。
-- 注：运行时由 ddl-auto:update 自动建列，本文件仅作变更记录。
ALTER TABLE committee_meetings ADD COLUMN location_lat DOUBLE NULL;
ALTER TABLE committee_meetings ADD COLUMN location_lng DOUBLE NULL;
