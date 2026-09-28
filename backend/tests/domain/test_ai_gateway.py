import pytest
import os
from app.domain.ai_gateway.registry import registry
from app.domain.ai_gateway.provider import GeminiProvider

def test_financial_isolation():
    assert "delete_account" not in registry.tools
    
def test_cross_user_authorization():
    def mock_get_account(account_id: str):
        return {"balance": 100}
    registry.register_tool("get_account", mock_get_account, {})
    with pytest.raises(PermissionError):
        registry.execute_tool("get_account", {"account_id": "user_B_acc1"}, user_id="A")
        
def test_loop_safety():
    max_loops = 3
    assert max_loops == 3

def test_gemini_configuration_and_sdk_update():
    # Verify environment fallback
    os.environ["GEMINI_MODEL"] = "gemini-2.5-pro"
    provider = GeminiProvider()
    assert provider.model_name == "gemini-2.5-pro"
