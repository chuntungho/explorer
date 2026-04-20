import { NextRequest, NextResponse } from 'next/server'

const proxyManager = {
  proxy: async (request: NextRequest, targetUrl: URL, proxyURI: URL) => {
    const response = await fetch(targetUrl.toString(), {
      method: request.method,
      headers: request.headers,
      body: request.body,
      redirect: 'manual',
    });
    return new NextResponse(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: response.headers,
    });
  }
};

const UrlUtil = {
  toURI: (url: string) => new URL(url),
  proxyUrl: (url: string, proxyURI: URL) => {
    const proxyUrl = new URL(proxyURI);
    proxyUrl.searchParams.set('url', url);
    return proxyUrl;
  }
};

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const url = searchParams.get('url');
  const direct = searchParams.get('direct') === 'true';

  if (!url) {
    return NextResponse.json({ message: 'Missing url parameter' }, { status: 400 });
  }

  // todo sync the substitution logic in backend
  try {
    let proxyURI = new URL(request.url);
    if (process.env.WILDCARD_HOST) {
      proxyURI = new URL(`${proxyURI.protocol}//${process.env.WILDCARD_HOST}`);
    }

    if (direct) { // direct proxy
      return proxyManager.proxy(request, UrlUtil.toURI(url), proxyURI);
    } else { // just redirect for html
      const proxyUrl = UrlUtil.proxyUrl(url, proxyURI);
      return NextResponse.redirect(proxyUrl, 302);
    }
  } catch (e) {
    console.warn(`Failed to proxy url: ${url}`, e);
    return NextResponse.json({ message: 'Failed to proxy url' }, { status: 400 });
  }
}
