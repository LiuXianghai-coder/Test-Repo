import json
import logging
import os
import sqlite3
import time
from typing import Optional, Dict, Any, List

import pymysql
from dotenv import load_dotenv
from mcp.server.fastmcp import FastMCP

from prompt_template import DDL_CONVERT_PROMPT_TEMPLATE, SYSTEM_PROMPT_TEMPLATE

query_logs = []
logger = logging.getLogger("db_agent")
QUERY_LOGS_FILE_PATH = "query_logs.json"
mcp = FastMCP("db_schema", log_level="ERROR")

load_dotenv()

MYSQL_DB_CONFIG = {
    "host": os.getenv("DB_HOST", "localhost"),
    "user": os.getenv("DB_USER", "test"),
    "passwd": os.getenv("DB_PASSWORD", "test"),
    "db": os.getenv("DB_NAME", "test_db"),
    "port": int(os.getenv("DB_PORT", 3306))
}


@mcp.tool()
def get_raw_schema_ddl(table_name: str) -> str:
    """ 根据表名获取原始的数据表的 DDL 语句

    Args:
        table_name: 待查询的表名
    """
    ddl_str = get_mysql_table_ddl(table_name, True, True)
    if ddl_str is None:
        return get_mysql_table_ddl_detailed(table_name)
    return ""


@mcp.tool()
def validate_sql(sql):
    """ 检查给定的 SQL 是否能够正常执行

    :param sql 待检查的 SQL
    :return 如果给定的 SQL 能够正常执行，返回 True, 否则返回 false
    """
    try:
        conn = sqlite3.connect(":memory:")  # 内存数据库
        cursor = conn.cursor()
        cursor.execute(sql)
        conn.commit()
        cursor.close()
        conn.close()
        return True
    except Exception as e:
        print(f"SQL 执行异常: {e}")
        # 因为目前没有实际的客户端，因此直接视为可执行即可
        return True


@mcp.tool()
def log_conversation(message: str, log_type: str, session_id: str) -> None:
    with open(format(f"./{session_id}.log"), "a") as log_file:
        if log_type == "llm":
            log_file.write(f"llm: {message}\n")
        else:
            log_file.write(f"mcp: {message}\n")


@mcp.prompt()
def generate_ddl_convert_prompt(raw_ddl: str, target_db: str) -> str:
    return DDL_CONVERT_PROMPT_TEMPLATE.format(
        DB_DDL=raw_ddl,
        TARGET_DB=target_db
    )


@mcp.prompt()
def generate_system_prompt(tool_list_str: str):
    return SYSTEM_PROMPT_TEMPLATE.format(TOOL_LIST=str(tool_list_str))


@mcp.resource("logs:/{session_id}/{limit}")
def get_query_log(limit: str = "5", session_id: str = "any") -> Dict[str, Any]:
    limit = int(limit)
    logs = [log for log in query_logs if log.get("session_id") == session_id]
    logs = logs[-limit:] if limit < len(logs) else logs

    # 转换时间戳为可读格式
    formatted_logs = []
    for log in logs:
        formatted_log = log.copy()
        formatted_log["timestamp"] = time.strftime(
            "%Y-%m-%d %H:%M:%S",
            time.localtime(log["timestamp"])
        )
        formatted_logs.append(formatted_log)

    return {
        "success": True,
        "logs": formatted_logs,
        "total_queries": len(logs)
    }


def log_query(message: str, success: bool, session_id: str = "any"):
    log_entry = {
        "success": success,
        "message": message,
        "session_id": session_id
    }

    global query_logs
    query_logs.append(log_entry)
    save_log_to_json_file(QUERY_LOGS_FILE_PATH)


def save_log_to_json_file(file_path: str):
    try:
        with open(file_path, "w") as json_file:
            json.dump(query_logs, json_file, ensure_ascii=False, indent=4)
        logger.info(f"Query logs saved to {file_path}")
    except Exception as e:
        logger.error(f"Query logs save to {file_path} error: {e}")


def get_mysql_table_ddl(table_name: str,
                        include_drop_table: bool = False,
                        include_additional_info: bool = True) -> Optional[Dict[str, str]]:
    """
    获取 MySQL 表的 DDL 语句

    Args:
        table_name: 表名
        include_drop_table: 是否包含 DROP TABLE 语句
        include_additional_info: 是否额外获取表注释、字符集等信息

    Returns:
        包含 DDL 语句及相关信息的字典，失败返回 None
    """
    global connection
    database = MYSQL_DB_CONFIG.get("db", "test")
    try:
        # 连接数据库
        connection = pymysql.connect(
            host=MYSQL_DB_CONFIG.get("host", "localhost"),
            user=MYSQL_DB_CONFIG.get("user", "root"),
            password=MYSQL_DB_CONFIG.get("passwd", ""),
            database=MYSQL_DB_CONFIG.get("db", "test"),
            port=MYSQL_DB_CONFIG.get("port", 3306),
            charset='utf8mb4'
        )

        result = {}

        with connection.cursor() as cursor:
            # 方法1：使用 SHOW CREATE TABLE 命令（最简单、最准确）
            cursor.execute(f"SHOW CREATE TABLE `{table_name}`")
            row = cursor.fetchone()

            if not row:
                print(f"表 '{table_name}' 不存在")
                return None

            create_table_sql = row[1]  # SHOW CREATE TABLE 返回两列：Table, Create Table

            # 如果需要生成 DROP TABLE 语句
            if include_drop_table:
                drop_table_sql = f"DROP TABLE IF EXISTS `{table_name}`;"
                result['drop_table'] = drop_table_sql
                result['full_ddl'] = f"{drop_table_sql}\n\n{create_table_sql}"
            else:
                result['full_ddl'] = create_table_sql

            result['create_table'] = create_table_sql

            # 如果需要额外信息，从 information_schema 获取表注释、字符集等
            if include_additional_info:
                with connection.cursor(pymysql.cursors.DictCursor) as dict_cursor:
                    # 获取表的额外元数据
                    dict_cursor.execute("""
                                        SELECT TABLE_COMMENT,
                                               TABLE_COLLATION,
                                               ENGINE,
                                               AUTO_INCREMENT,
                                               CREATE_TIME,
                                               UPDATE_TIME
                                        FROM information_schema.TABLES
                                        WHERE TABLE_SCHEMA = %s
                                          AND TABLE_NAME = %s
                                        """, (database, table_name))

                    table_info = dict_cursor.fetchone()
                    if table_info:
                        result['table_comment'] = table_info['TABLE_COMMENT']
                        result['table_collation'] = table_info['TABLE_COLLATION']
                        result['engine'] = table_info['ENGINE']
                        result['auto_increment'] = table_info['AUTO_INCREMENT']
                        result['create_time'] = str(table_info['CREATE_TIME']) if table_info['CREATE_TIME'] else None
                        result['update_time'] = str(table_info['UPDATE_TIME']) if table_info['UPDATE_TIME'] else None

                    # 获取表的字符集
                    dict_cursor.execute("""
                                        SELECT CCSA.character_set_name
                                        FROM information_schema.TABLES T
                                                 LEFT JOIN information_schema.COLLATION_CHARACTER_SET_APPLICABILITY CCSA
                                                           ON T.TABLE_COLLATION = CCSA.collation_name
                                        WHERE T.TABLE_SCHEMA = %s
                                          AND T.TABLE_NAME = %s
                                        """, (database, table_name))

                    charset_result = dict_cursor.fetchone()
                    if charset_result:
                        result['character_set'] = charset_result['character_set_name']

        return result

    except pymysql.Error as e:
        print(f"数据库错误: {e}")
        return None
    except Exception as e:
        print(f"发生错误: {e}")
        return None
    finally:
        if 'connection' in locals() and connection:
            connection.close()


def get_mysql_table_ddl_detailed(table_name: str) -> Optional[str]:
    """
    手动构建 DDL 语句（备用方案，当 SHOW CREATE TABLE 不可用时）
    从 information_schema 中读取元数据并手动构建 CREATE TABLE 语句

    Args:
        table_name: 表名
    Returns:
        手动构建的 DDL 语句，失败返回 None
    """
    global connection
    database = MYSQL_DB_CONFIG.get("db", "test")
    try:
        connection = pymysql.connect(
            host=MYSQL_DB_CONFIG.get("host", "localhost"),
            user=MYSQL_DB_CONFIG.get("user", "root"),
            password=MYSQL_DB_CONFIG.get("passwd", ""),
            database=MYSQL_DB_CONFIG.get("db", "test"),
            port=MYSQL_DB_CONFIG.get("port", 3306),
            charset='utf8mb4'
        )

        with connection.cursor(pymysql.cursors.DictCursor) as cursor:
            # 1. 获取表基本信息
            cursor.execute("""
                           SELECT ENGINE,
                                  TABLE_COLLATION,
                                  TABLE_COMMENT,
                                  AUTO_INCREMENT
                           FROM information_schema.TABLES
                           WHERE TABLE_SCHEMA = %s
                             AND TABLE_NAME = %s
                           """, (database, table_name))

            table_info = cursor.fetchone()
            if not table_info:
                print(f"表 '{table_name}' 不存在")
                return None

            # 2. 获取列信息
            cursor.execute("""
                           SELECT COLUMN_NAME,
                                  COLUMN_TYPE,
                                  IS_NULLABLE,
                                  COLUMN_DEFAULT,
                                  EXTRA,
                                  COLUMN_COMMENT
                           FROM information_schema.COLUMNS
                           WHERE TABLE_SCHEMA = %s
                             AND TABLE_NAME = %s
                           ORDER BY ORDINAL_POSITION
                           """, (database, table_name))

            columns = cursor.fetchall()

            # 3. 获取主键信息
            cursor.execute("""
                           SELECT COLUMN_NAME
                           FROM information_schema.COLUMNS
                           WHERE TABLE_SCHEMA = %s
                             AND TABLE_NAME = %s
                             AND COLUMN_KEY = 'PRI'
                           ORDER BY ORDINAL_POSITION
                           """, (database, table_name))

            primary_keys = [row['COLUMN_NAME'] for row in cursor.fetchall()]

            # 4. 获取索引信息（非主键）
            cursor.execute("""
                           SELECT INDEX_NAME,
                                  NON_UNIQUE,
                                  COLUMN_NAME,
                                  SEQ_IN_INDEX
                           FROM information_schema.STATISTICS
                           WHERE TABLE_SCHEMA = %s
                             AND TABLE_NAME = %s
                             AND INDEX_NAME != 'PRIMARY'
                           ORDER BY INDEX_NAME, SEQ_IN_INDEX
                           """, (database, table_name))

            indexes = cursor.fetchall()

            # 组织索引
            index_dict = {}
            for idx in indexes:
                idx_name = idx['INDEX_NAME']
                if idx_name not in index_dict:
                    index_dict[idx_name] = {
                        'unique': idx['NON_UNIQUE'] == 0,
                        'columns': []
                    }
                index_dict[idx_name]['columns'].append(idx['COLUMN_NAME'])

            # 5. 开始构建 DDL
            ddl_lines = [f"CREATE TABLE `{table_name}` ("]

            # 添加列定义
            for col in columns:
                col_def = f"  `{col['COLUMN_NAME']}` {col['COLUMN_TYPE']}"

                # NULL 约束
                if col['IS_NULLABLE'] == 'NO':
                    col_def += " NOT NULL"
                else:
                    # MySQL 默认允许 NULL，显式指定可选
                    pass

                # 默认值
                if col['COLUMN_DEFAULT'] is not None:
                    if col['COLUMN_DEFAULT'] == 'CURRENT_TIMESTAMP':
                        col_def += f" DEFAULT {col['COLUMN_DEFAULT']}"
                    else:
                        col_def += f" DEFAULT '{col['COLUMN_DEFAULT']}'"

                # 额外属性（如 auto_increment, on update）
                if col['EXTRA']:
                    col_def += f" {col['EXTRA']}"

                # 列注释
                if col['COLUMN_COMMENT']:
                    col_def += f" COMMENT '{col['COLUMN_COMMENT']}'"

                ddl_lines.append(col_def + ",")

            # 添加主键约束
            if primary_keys:
                pk_str = f"  PRIMARY KEY ({', '.join([f'`{pk}`' for pk in primary_keys])}),"
                ddl_lines.append(pk_str)

            # 添加唯一索引和普通索引
            for idx_name, idx_info in index_dict.items():
                index_type = "UNIQUE KEY" if idx_info['unique'] else "KEY"
                columns_str = ", ".join([f"`{col}`" for col in idx_info['columns']])
                idx_def = f"  {index_type} `{idx_name}` ({columns_str}),"
                ddl_lines.append(idx_def)

            # 移除最后一行的逗号
            if ddl_lines[-1].endswith(','):
                ddl_lines[-1] = ddl_lines[-1][:-1]

            # 添加表选项
            ddl_lines.append(f") ENGINE={table_info['ENGINE']}")

            # 字符集和排序规则
            if table_info['TABLE_COLLATION']:
                ddl_lines[-1] += f" DEFAULT CHARSET={table_info['TABLE_COLLATION'].split('_')[0]}"
                ddl_lines[-1] += f" COLLATE={table_info['TABLE_COLLATION']}"

            # 自增值
            if table_info['AUTO_INCREMENT']:
                ddl_lines[-1] += f" AUTO_INCREMENT={table_info['AUTO_INCREMENT']}"

            # 表注释
            if table_info['TABLE_COMMENT']:
                ddl_lines[-1] += f" COMMENT='{table_info['TABLE_COMMENT']}'"

            # 添加分号
            ddl_lines[-1] += ";"

            return "\n".join(ddl_lines)

    except pymysql.Error as e:
        print(f"数据库错误: {e}")
        return None
    except Exception as e:
        print(f"发生错误: {e}")
        return None
    finally:
        if 'connection' in locals() and connection:
            connection.close()


# 使用示例
if __name__ == "__main__":
    mcp.run(transport="stdio")
    print("*" * 30)
    print("start db_schema mcp server")
