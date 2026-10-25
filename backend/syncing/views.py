from rest_framework import status
from rest_framework.response import Response
from rest_framework.views import APIView

from .serializers import SyncRequestSerializer
from .services import apply_sync, build_sync_snapshot


class SyncView(APIView):
    def get(self, request):
        return Response(build_sync_snapshot(request.user))

    def post(self, request):
        serializer = SyncRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        apply_sync(request.user, serializer.validated_data)
        return Response(build_sync_snapshot(request.user), status=status.HTTP_200_OK)
