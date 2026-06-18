# 业委会会议 KB v2.0

本目录包含 3 个核心 JSON 文件：

## 1. topic_kb.json
用于存储会议议题。

字段说明：
- topic：二级议题名称
- parent_topic：一级议题
- fields：该议题常见结构化字段
- keywords：用于关键词匹配和 embedding 的关键词

## 2. synonym_kb.json
用于存储标准术语和同义词。

例如：
“专项维修资金” 可以归一化为 “公共维修基金”。
“原则同意” 可以归一化为 “通过”。

## 3. rule_kb.json
用于存储规则。

主要包括：
- 金额提取
- 时间提取
- 表决结果判断
- 资金来源判断
- 责任方判断
- 公司/单位提取
- 楼栋/位置提取

## 推荐使用流程

ASR 语音转文字
↓
topic_kb 匹配议题
↓
synonym_kb 归一化术语
↓
rule_kb 提取字段
↓
embedding 做语义相似度补充
↓
大模型兜底
↓
输出 JSON

## Python 读取示例

```python
import json

def load_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)

topic_kb = load_json("kb/topic_kb.json")
synonym_kb = load_json("kb/synonym_kb.json")
rule_kb = load_json("kb/rule_kb.json")
```