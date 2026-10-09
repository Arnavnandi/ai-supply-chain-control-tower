export default {
  async fetch(request, env, ctx) {
    const response = await env.ASSETS.fetch(request);
    if (response.status === 404) {
      const url = new URL(request.url);
      const filename = url.pathname.split('/').pop();
      // If it's a SPA route (no extension like .js, .css, .svg), return index.html
      if (!filename.includes('.')) {
        return env.ASSETS.fetch(new Request(new URL('/index.html', request.url), request));
      }
    }
    return response;
  }
};
