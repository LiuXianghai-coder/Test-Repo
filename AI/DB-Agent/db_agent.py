import asyncio
import json
import os
import re
import uuid
from contextlib import AsyncExitStack
from typing import Optional

from dotenv import load_dotenv
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client
from openai import OpenAI
from pydantic import AnyUrl

load_dotenv()

API_CONFIG = {
    "baseUrl": os.getenv("BASE_URL", "https://api.deepseek.com"),
    "apiKey": os.getenv("API_KEY", ""),
}


def print_help():
    """Print help message"""
    print("\n=== 使用说明 ===")
    print("支持的命令：")
    print("1. ddl [targetDB] 表名 - 将指定的表的DDL转换为目标数据库适配的 DDL")
    print("2. log [数量] - 查询最近的一操作日志")
    print("3. quit 退出当前会话")


class DBAgent:
    def __init__(self):
        self.stdio = None
        self.write = None
        self.session: Optional[ClientSession] = None
        self.exit_stack = AsyncExitStack()
        self.session_id = str(uuid.uuid4())
        self.model = OpenAI(base_url=API_CONFIG.get("baseUrl"), api_key=API_CONFIG.get("apiKey"))
        self.messages = []

    async def connect_to_server(self, server_script_path: str):
        """Connect to an MCP server

        Args:
            server_script_path: Path to the server script (.py or .js)
        """

        server_params = StdioServerParameters(
            command="python",
            args=[server_script_path],
            env=None
        )
        stdio_transport = await self.exit_stack.enter_async_context(stdio_client(server_params))
        print("stdio_transport", stdio_transport)
        self.stdio, self.write = stdio_transport
        self.session = await self.exit_stack.enter_async_context(ClientSession(self.stdio, self.write))
        await self.session.initialize()
        print(f"Session_id: {self.session_id}")

        # List available tools
        response = await self.session.list_tools()
        tools = response.tools
        print("\nConnected to server with tools:", [tool.name for tool in tools])

        # List available resources
        resources_response = await self.session.list_resources()
        if resources_response and resources_response.resources:
            print("Available resources:", [resource.uri for resource in resources_response.resources])
        else:
            print("Available resources templates: ['logs']")

        prompts = await self.session.list_prompts()
        if prompts and prompts.prompts:
            print("Available prompts:", [prompt.name for prompt in prompts.prompts])
        else:
            print("No available prompts found.")

        tool_template_list = []
        for tool in tools:
            tool_template_list.append(f"{tool.name}({tool.inputSchema.keys()})")
        sys_prompt_result = await self.session.get_prompt("generate_system_prompt", {
            "tool_list_str": str(tool_template_list)
        })
        self.messages.append({"role": "system", "content": sys_prompt_result.messages[0].content.text})

    async def process_ddl_convert_query(self, target_db: str, table_name: str) -> str:
        ddl_result = await self.session.call_tool("get_raw_schema_ddl", {"table_name": table_name})
        ddl_sql = ddl_result.content[0].text

        convert_prompt = await self.session.get_prompt("generate_ddl_convert_prompt", {
            "raw_ddl": ddl_sql,
            "target_db": target_db
        })
        # print("convert_prompt ", convert_prompt)
        self.messages.append(
            {
                "role": "user",
                "content": convert_prompt.messages[0].content.text
            }
        )

        while True:
            print("messages: ", self.messages)
            response = self.call_model(self.messages)
            json_content = json.loads(response)
            # 检测模型是否输出 Final Answer，如果是的话，直接返回
            if json_content.get("final_answer") is not None:
                return json_content["final_answer"]

            if json_content.get("action") is not None:
                call_result = await self.session.call_tool(json_content["action"], {
                    "sql": json_content.get("action_params")["sql"],
                    "session_id": self.session_id
                })
                observer_content = format(f'''{{"observation": {call_result.content[0].text}}}''')
                self.messages.append({"role": "user", "content": observer_content})

            self.messages.append({"role": "assistant", "content": response})

    async def query_logs(self, limit: str) -> str:
        logs_response = await self.session.read_resource(AnyUrl(f"logs://{self.session_id}/{limit}"))
        logs_info = json.loads(logs_response.contents[0].text)
        logs = logs_info.get("logs", [])

        result = [f"\n最近的{len(logs)}条查询日志:"]
        for log in logs:
            result.append(f"\n时间: {log['timestamp']}")
            result.append(f"操作: {log['operation']}")
            result.append(f"状态: {'成功' if log['success'] else '失败'}")
            if not log['success'] and log.get('error'):
                result.append(f"错误信息: {log['error']}")
            result.append("-" * 40)

        return "\n".join(result)

    def call_model(self, messages) -> str | None:
        print("正在请求模型" + "." * 6)
        try:
            response = self.model.chat.completions.create(
                model="deepseek-v4-pro",
                messages=messages,
                reasoning_effort="high",
                extra_body={"thinking": {"type": "enabled"}}
            )
            content = response.choices[0].message.content
            print("response:", content)
            return content
        except Exception as e:
            print("调用 LLM 时出现异常", e)

    async def chat_loop(self):
        print("\nMCP Client Started!")
        print_help()
        while True:
            query = input("\nQuery: ")
            if query.lower().startswith("quit"):
                break

            if query.lower().startswith("ddl"):
                target_db = re.split(r"\s+", query.lower())[1]
                table_name = re.split(r"\s+", query.lower())[2]
                target_ddl = await self.process_ddl_convert_query(target_db, table_name)
                print("target_ddl: {}", target_ddl)

            if query.lower().startswith("log"):
                limit = re.split(r"\s+", query.lower())[1]
                query_log_list = await self.query_logs(limit)
                print(f"query_log_list: {query_log_list}")

    async def cleanup(self):
        """Clean up resources"""
        await self.exit_stack.aclose()


async def main():
    if len(sys.argv) < 2:
        print("Usage: python client.py <path_to_server_script>")
        sys.exit(1)

    client = DBAgent()
    try:
        await client.connect_to_server(sys.argv[1])
        await client.chat_loop()
    finally:
        await client.cleanup()


if __name__ == '__main__':
    import sys

    asyncio.run(main())
